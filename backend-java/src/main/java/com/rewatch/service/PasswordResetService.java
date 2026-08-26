package com.rewatch.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

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

        // Only the hash is persisted — the raw token exists solely in this
        // request's memory and the email it's about to go out in. A 256-bit
        // random token is already infeasible to brute-force from its hash
        // (unlike a password, so no slow KDF is needed here), but a raw
        // value in the database means anyone who can read that table — a
        // backup, a leaked dump, a future bug in an admin export — gets a
        // directly usable, unexpired reset token with no extra step.
        Instant now = Instant.now();
        tokenRepo.save(new PasswordResetToken(user.getId(), hashToken(token), now.plus(EXPIRY_MINUTES, ChronoUnit.MINUTES), now));

        String resetLink = frontendBaseUrl + "/reset-password?token=" + token;
        try {
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetLink);
        } catch (Exception e) {
            // EmailService.send() no longer lets an ordinary delivery
            // failure reach here at all (it records an EmailDeliveryRecord
            // and logs its own correlation id instead of throwing) — this
            // is now a backstop for a genuinely unexpected exception in
            // sendPasswordResetEmail itself, not the normal SMTP-down path.
            // Never log the link/token here regardless: this exact line
            // used to, and that's live in Render's log retention now.
            log.error("Unexpected exception while sending password reset email to user {}", user.getId(), e);
        }
    }

    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is a JDK-mandatory algorithm", e);
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }

        PasswordResetToken token = tokenRepo.findByToken(hashToken(rawToken)).orElse(null);
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
