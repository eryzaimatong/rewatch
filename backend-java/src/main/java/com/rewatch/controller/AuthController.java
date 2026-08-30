package com.rewatch.controller;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.model.User;
import com.rewatch.repository.UserRepository;
import com.rewatch.security.JwtService;
import com.rewatch.security.RateLimiterService;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.PasswordResetService;

/**
 * Real auth: passwords are BCrypt-hashed at rest, compared via the encoder (not
 * String.equals), and a JWT is issued on success. Neither endpoint ever echoes
 * the password back — the response is a purpose-built map, not the User entity,
 * and User.password also carries @JsonIgnore as a second line of defense.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetService passwordResetService;
    private final RateLimiterService rateLimiter;
    private final List<String> adminEmails;

    public AuthController(UserRepository userRepo, PasswordEncoder passwordEncoder, JwtService jwtService,
                          PasswordResetService passwordResetService, RateLimiterService rateLimiter,
                          @Value("${rewatch.admin.emails}") String adminEmailsCsv) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.passwordResetService = passwordResetService;
        this.rateLimiter = rateLimiter;
        this.adminEmails = Arrays.stream(adminEmailsCsv.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isBlank())
                .toList();
    }

    // Carrier-grade NAT means thousands of real subscribers can share a
    // handful of public IPs — a shared link or a group of friends signing up
    // together from the same carrier gateway must not exhaust an IP-keyed
    // budget meant for a single abuser. Six people registering from one
    // group chat is the success case, not an attack, so this stays generous;
    // it still bounds a scripted mass-registration run.
    private static final int MAX_REGISTER_PER_HOUR = 50;
    private static final int MAX_LOGIN_PER_15_MIN = 100;
    // The limit that actually stops credential stuffing — repeated wrong
    // guesses against ONE account — since the IP-keyed limit above was never
    // doing that job for anyone behind shared NAT; it only ever bounded a
    // single machine. Counts failures only (see the recordAttempt() call
    // below), not every login attempt, so a legitimate user retyping a
    // remembered-wrong password a few times doesn't get anywhere near it,
    // and a successful login never counts against it at all.
    private static final int MAX_LOGIN_FAILURES_PER_ACCOUNT = 10;
    private static final Duration LOGIN_ACCOUNT_WINDOW = Duration.ofMinutes(15);
    // Two separate keys, both required to have budget (see forgotPassword
    // below) — not an either/or escape valve, an earlier read of this code
    // got that backwards. The email-keyed limit is what actually stops
    // abuse (hammering one victim's inbox from many IPs); the IP-keyed one
    // was only ever a blunt second line, and on carrier NAT it punishes
    // strangers for each other's password resets — someone else on the same
    // carrier gateway exhausting the IP budget blocks your request even
    // though your own email has never been used. Raised well past the
    // email limit so the IP gate stops mattering for any realistic shared-
    // NAT scenario, while the email limit (unchanged) keeps doing the real
    // work.
    private static final int MAX_FORGOT_PASSWORD_PER_HOUR_BY_IP = 50;
    private static final int MAX_FORGOT_PASSWORD_PER_HOUR_BY_EMAIL = 3;
    // Generous relative to forgot-password: a legit user can retry a typo'd new
    // password several times against the same valid link without tripping this,
    // while still bounding how many guesses an attacker gets against one token.
    // Single IP-keyed gate only — no second key, so no AND-composition
    // concern here the way forgot-password had.
    private static final int MAX_RESET_PASSWORD_PER_HOUR = 10;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody User user, HttpServletRequest request) {
        String key = "register:" + SecurityUtil.clientIp(request);
        if (!rateLimiter.allow(key, MAX_REGISTER_PER_HOUR, Duration.ofHours(1))) {
            return rateLimiter.tooManyRequests(key, Duration.ofHours(1), "Too many registration attempts from your network.");
        }
        if (user.getPassword() == null || user.getPassword().length() < 6) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", "Password must be at least 6 characters."));
        }
        if (userRepo.findByEmail(user.getEmail()) != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", "Email is already registered!"));
        }
        if (user.getUsername() != null && userRepo.findByUsername(user.getUsername()) != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", "Username is already taken!"));
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (isAdminEmail(user.getEmail())) {
            user.setRole(User.Role.ADMIN);
        }

        // No TasteDNA row to seed anymore: a freshly registered user simply has no
        // UserTrait rows yet, and ProfileService.currentProfile() correctly falls
        // back to the neutral profile until onboarding or a rating writes real data.
        User savedUser = userRepo.save(user);
        return ResponseEntity.ok(sessionResponse(savedUser, "Registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials, HttpServletRequest request) {
        String ipKey = "login:" + SecurityUtil.clientIp(request);
        if (!rateLimiter.allow(ipKey, MAX_LOGIN_PER_15_MIN, Duration.ofMinutes(15))) {
            return rateLimiter.tooManyRequests(ipKey, Duration.ofMinutes(15), "Too many login attempts from your network.");
        }
        String loginInput = credentials.get("username");
        String password = credentials.get("password");

        User user = userRepo.findByEmail(loginInput);
        if (user == null) {
            user = userRepo.findByUsername(loginInput);
        }

        // Keyed on the resolved account when one exists — unifies email vs
        // username entry for the same account — falling back to the raw
        // submitted identifier when no account matches at all, so guessing
        // against a nonexistent account is still bounded, just less precisely.
        String accountKey = "login-account:" + (user != null
                ? user.getId().toString()
                : (loginInput == null ? "" : loginInput.trim().toLowerCase()));
        if (rateLimiter.isBlocked(accountKey, MAX_LOGIN_FAILURES_PER_ACCOUNT, LOGIN_ACCOUNT_WINDOW)) {
            return rateLimiter.tooManyRequests(accountKey, LOGIN_ACCOUNT_WINDOW, "Too many failed attempts for this account.");
        }

        if (user != null && password != null && passwordEncoder.matches(password, user.getPassword())) {
            // Self-healing admin grant: covers the realistic case of ADMIN_EMAILS
            // being set (or changed) after the account already exists, since you
            // can't register against an env var that isn't set yet on a fresh deploy.
            if (isAdminEmail(user.getEmail()) && user.getRole() != User.Role.ADMIN) {
                user.setRole(User.Role.ADMIN);
                user = userRepo.save(user);
            }
            return ResponseEntity.ok(sessionResponse(user, "Login successful"));
        }

        // Only a genuine failure counts against the account — a successful
        // login never touches this budget, so this can never lock out the
        // account's real owner just for logging in normally.
        rateLimiter.recordAttempt(accountKey);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Invalid username/email or password"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email = body.get("email") == null ? "" : body.get("email").trim().toLowerCase();
        // Both keys checked: the IP limit stops one attacker spraying many
        // addresses, the email limit stops a real Gmail sending account from
        // being used to bomb one specific victim's inbox from many IPs.
        String ipKey = "forgot-ip:" + SecurityUtil.clientIp(request);
        String emailKey = "forgot-email:" + email;
        boolean ipOk = rateLimiter.allow(ipKey, MAX_FORGOT_PASSWORD_PER_HOUR_BY_IP, Duration.ofHours(1));
        boolean emailOk = email.isBlank()
                || rateLimiter.allow(emailKey, MAX_FORGOT_PASSWORD_PER_HOUR_BY_EMAIL, Duration.ofHours(1));
        if (!ipOk || !emailOk) {
            // Whichever gate actually failed drives the retry-after estimate
            // shown — if both did, the IP one is reported (it's the one a
            // shared-network visitor is more likely to be waiting on).
            String blockedKey = !ipOk ? ipKey : emailKey;
            return rateLimiter.tooManyRequests(blockedKey, Duration.ofHours(1), "Too many password reset requests.");
        }
        passwordResetService.requestReset(body.get("email"));
        return ResponseEntity.ok(Map.of("status", "success",
                "message", "If that email is registered, a reset link is on its way."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String key = "reset:" + SecurityUtil.clientIp(request);
        if (!rateLimiter.allow(key, MAX_RESET_PASSWORD_PER_HOUR, Duration.ofHours(1))) {
            return rateLimiter.tooManyRequests(key, Duration.ofHours(1), "Too many password reset attempts.");
        }
        try {
            passwordResetService.resetPassword(body.get("token"), body.get("newPassword"));
            return ResponseEntity.ok(Map.of("status", "success", "message", "Password reset. Please log in."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    private boolean isAdminEmail(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase());
    }

    private Map<String, Object> sessionResponse(User user, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("userId", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        // Whether onboarding has already run for this account, server-side. The
        // frontend used to track this purely in localStorage, which meant clearing
        // storage or logging in on a new device re-triggered onboarding for an
        // account that already has a real, rating-built profile — and completing it
        // again silently overwrote that profile via seedFromOnboarding.
        response.put("onboarded", user.getSeedVector() != null);
        response.put("accentColor", user.getAccentColor() == null ? "purple" : user.getAccentColor());
        response.put("avatarUrl", user.getAvatarUrl());
        response.put("avatarFrame", user.getAvatarFrame());
        response.put("nickname", user.getNickname());
        // Read-only signal for the frontend to show/hide the admin reports entry
        // point — purely a UX convenience. The actual authorization is the
        // route-level hasRole("ADMIN") check on /api/admin/** (SecurityConfig),
        // re-derived from the live DB row on every request by JwtAuthFilter, so
        // this value being stale or tampered with client-side grants nothing.
        response.put("role", user.getRole().name());
        response.put("token", jwtService.issue(user.getId(), user.getUsername(), user.getTokenVersion()));
        return response;
    }
}
