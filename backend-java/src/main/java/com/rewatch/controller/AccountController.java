package com.rewatch.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.dto.AccountRequests.ChangePassword;
import com.rewatch.dto.AccountRequests.DeleteAccount;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.AccountService;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePassword req, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        try {
            String token = accountService.changePassword(req.getUserId(), req.getCurrentPassword(), req.getNewPassword());
            return ResponseEntity.ok(Map.of("status", "success", "token", token));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deleteAccount(@Valid @RequestBody DeleteAccount req, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        try {
            accountService.deleteAccount(req.getUserId(), req.getPassword());
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
