package com.rewatch.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.dto.AccountRequests.ChangePassword;
import com.rewatch.dto.AccountRequests.DeleteAccount;
import com.rewatch.dto.AccountRequests.SetAccentColor;
import com.rewatch.dto.AccountRequests.SetAvatar;
import com.rewatch.dto.AccountRequests.SetProfileTheme;
import com.rewatch.dto.AccountRequests.SetAvatarFrame;
import com.rewatch.dto.AccountRequests.SetNickname;
import com.rewatch.dto.AccountRequests.SetPinnedContent;
import com.rewatch.dto.AccountRequests.SetProfileVisibility;
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

    @GetMapping("/profile-visibility/{userId}")
    public ResponseEntity<?> getProfileVisibility(@PathVariable Long userId, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        return ResponseEntity.ok(Map.of("public", accountService.getProfileVisibility(userId)));
    }

    @PostMapping("/profile-visibility")
    public ResponseEntity<?> setProfileVisibility(@Valid @RequestBody SetProfileVisibility req, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        accountService.setProfileVisibility(req.getUserId(), req.getIsPublic());
        return ResponseEntity.ok(Map.of("status", "success", "public", req.getIsPublic()));
    }

    @PostMapping("/accent-color")
    public ResponseEntity<?> setAccentColor(@Valid @RequestBody SetAccentColor req, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        try {
            accountService.setAccentColor(req.getUserId(), req.getAccentColor());
            return ResponseEntity.ok(Map.of("status", "success", "accentColor", req.getAccentColor()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/profile-theme")
    public ResponseEntity<?> setProfileTheme(@Valid @RequestBody SetProfileTheme req, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        try {
            accountService.setProfileTheme(req.getUserId(), req.getProfileTheme());
            return ResponseEntity.ok(Map.of("status", "success", "profileTheme", req.getProfileTheme()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/avatar")
    public ResponseEntity<?> setAvatar(@Valid @RequestBody SetAvatar req, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        try {
            accountService.setAvatarUrl(req.getUserId(), req.getAvatarUrl());
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/avatar-frame")
    public ResponseEntity<?> setAvatarFrame(@Valid @RequestBody SetAvatarFrame req, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        try {
            accountService.setAvatarFrame(req.getUserId(), req.getFrame());
            return ResponseEntity.ok(Map.of("status", "success", "frame", req.getFrame() == null ? "" : req.getFrame()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/nickname")
    public ResponseEntity<?> setNickname(@Valid @RequestBody SetNickname req, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        try {
            accountService.setNickname(req.getUserId(), req.getNickname());
            return ResponseEntity.ok(Map.of("status", "success", "nickname", req.getNickname() == null ? "" : req.getNickname()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/pinned")
    public ResponseEntity<?> setPinnedContent(@Valid @RequestBody SetPinnedContent req, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        try {
            accountService.setPinnedContent(req.getUserId(), req.getTitleIds(), req.getRatingId(), req.getFolderId());
            return ResponseEntity.ok(Map.of("status", "success"));
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
