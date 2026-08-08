package com.rewatch.controller;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

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

    /** X-Forwarded-For first — a real deployment sits behind nginx/a load balancer, where getRemoteAddr() would just be that proxy's own address. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final int MAX_REGISTER_PER_HOUR = 5;
    private static final int MAX_LOGIN_PER_15_MIN = 10;
    private static final int MAX_FORGOT_PASSWORD_PER_HOUR = 3;

    private ResponseEntity<?> tooManyRequests() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("status", "error", "message", "Too many attempts. Please try again later."));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user, HttpServletRequest request) {
        if (!rateLimiter.allow("register:" + clientIp(request), MAX_REGISTER_PER_HOUR, Duration.ofHours(1))) {
            return tooManyRequests();
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
        if (!rateLimiter.allow("login:" + clientIp(request), MAX_LOGIN_PER_15_MIN, Duration.ofMinutes(15))) {
            return tooManyRequests();
        }
        String loginInput = credentials.get("username");
        String password = credentials.get("password");

        User user = userRepo.findByEmail(loginInput);
        if (user == null) {
            user = userRepo.findByUsername(loginInput);
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

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Invalid username/email or password"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email = body.get("email") == null ? "" : body.get("email").trim().toLowerCase();
        // Both keys checked: the IP limit stops one attacker spraying many
        // addresses, the email limit stops a real Gmail sending account from
        // being used to bomb one specific victim's inbox from many IPs.
        boolean ipOk = rateLimiter.allow("forgot-ip:" + clientIp(request), MAX_FORGOT_PASSWORD_PER_HOUR, Duration.ofHours(1));
        boolean emailOk = email.isBlank()
                || rateLimiter.allow("forgot-email:" + email, MAX_FORGOT_PASSWORD_PER_HOUR, Duration.ofHours(1));
        if (!ipOk || !emailOk) {
            return tooManyRequests();
        }
        passwordResetService.requestReset(body.get("email"));
        return ResponseEntity.ok(Map.of("status", "success",
                "message", "If that email is registered, a reset link is on its way."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
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
        response.put("token", jwtService.issue(user.getId(), user.getUsername(), user.getTokenVersion()));
        return response;
    }
}
