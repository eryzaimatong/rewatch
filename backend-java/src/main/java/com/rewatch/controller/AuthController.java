package com.rewatch.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.model.TasteDNA;
import com.rewatch.model.User;
import com.rewatch.repository.TasteDNARepository;
import com.rewatch.repository.UserRepository;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@SuppressWarnings("null")
public class AuthController {

    private final UserRepository userRepo;
    private final TasteDNARepository tasteRepo;

    public AuthController(UserRepository userRepo, TasteDNARepository tasteRepo) {
        this.userRepo = userRepo;
        this.tasteRepo = tasteRepo;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        // 1. Check if email or username is already taken
        if (userRepo.findByEmail(user.getEmail()) != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Email is already registered!");
        }
        if (user.getUsername() != null && userRepo.findByUsername(user.getUsername()) != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Username is already taken!");
        }

        // 2. Save the new user
        User savedUser = userRepo.save(user);

        // 3. Automatically create a starter TasteDNA profile for them
        TasteDNA initialDna = new TasteDNA();
        initialDna.setUserId(savedUser.getId());
        initialDna.setComfort(50);
        initialDna.setRomance(50);
        initialDna.setSlowBurn(50);
        initialDna.setNostalgia(50);
        initialDna.setSadness(50);
        tasteRepo.save(initialDna);

        return ResponseEntity.ok(savedUser);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String loginInput = credentials.get("username"); // can be email or username
        String password = credentials.get("password");

        User user = userRepo.findByEmail(loginInput);
        if (user == null) {
            user = userRepo.findByUsername(loginInput);
        }

        // Check if user exists and password matches
        if (user != null && user.getPassword().equals(password)) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Login successful");
            response.put("userId", user.getId());
            response.put("username", user.getUsername());
            response.put("email", user.getEmail());
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invalid username/email or password");
    }
}