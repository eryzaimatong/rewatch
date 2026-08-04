package com.rewatch.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.model.PasswordResetToken;
import com.rewatch.model.User;
import com.rewatch.repository.PasswordResetTokenRepository;
import com.rewatch.repository.UserRepository;

/**
 * Request/reset flow for a forgotten password. Deliberately does not signal
 * whether a given email is registered — requestReset() always "succeeds" from
 * the caller's point of view, whether or not an email actually goes out. This
 * is the standard fix for the user-enumeration hole a password-reset endpoint
 * would otherwise open (an attacker probing which emails have accounts).
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private static final int TOKEN_BYTES = 32;
    private static final long EXPIRY_MINUTES = 45;

    private final UserRepository userRepo;
    private final PasswordResetTokenRepository tokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String frontendBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository userRepo, PasswordResetTokenRepository tokenRepo,
                                PasswordEncoder passwordEncoder, EmailService emailService,
                                @Value("${rewatch.frontend.base-url}") String frontendBaseUrl) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public void requestReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        User user = userRepo.findByEmail(email.trim());
        if (user == null) {
            return;
        }

        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = Instant.now();
        tokenRepo.save(new PasswordResetToken(user.getId(), token, now.plus(EXPIRY_MINUTES, ChronoUnit.MINUTES), now));

        String resetLink = frontendBaseUrl + "/reset-password?token=" + token;
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetLink);
        } catch (Exception e) {
            // Deliberately swallowed, not rethrown: requestReset()'s whole contract
            // is "always looks like success to the caller" (see class javadoc) —
            // an SMTP failure (unset credentials, provider outage) must not turn
            // into a 500 that both breaks that contract and leaks which emails
            // are registered via a timing/error-shape difference. The token row
            // is already saved, so once mail delivery is fixed a matching
            // /reset-password link still works; this is a delivery failure, not
            // a lost request.
            log.error("Failed to send password reset email to user {} — link: {}", user.getId(), resetLink, e);
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }

        PasswordResetToken token = tokenRepo.findByToken(rawToken).orElse(null);
        if (token == null || !token.isValid(Instant.now())) {
            throw new IllegalArgumentException("Invalid or expired reset link.");
        }

        User user = userRepo.findById(token.getUserId()).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException("Invalid or expired reset link.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepo.save(user);

        token.setUsedAt(Instant.now());
        tokenRepo.save(token);
    }
}
