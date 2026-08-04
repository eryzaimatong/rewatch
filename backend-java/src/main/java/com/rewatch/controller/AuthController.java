package com.rewatch.controller;

import java.util.HashMap;
import java.util.Map;

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

    public AuthController(UserRepository userRepo, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
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

        // No TasteDNA row to seed anymore: a freshly registered user simply has no
        // UserTrait rows yet, and ProfileService.currentProfile() correctly falls
        // back to the neutral profile until onboarding or a rating writes real data.
        User savedUser = userRepo.save(user);
        return ResponseEntity.ok(sessionResponse(savedUser, "Registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String loginInput = credentials.get("username");
        String password = credentials.get("password");

        User user = userRepo.findByEmail(loginInput);
        if (user == null) {
            user = userRepo.findByUsername(loginInput);
        }

        if (user != null && password != null && passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.ok(sessionResponse(user, "Login successful"));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "error", "message", "Invalid username/email or password"));
    }

    private Map<String, Object> sessionResponse(User user, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("userId", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("token", jwtService.issue(user.getId(), user.getUsername()));
        return response;
    }
}
