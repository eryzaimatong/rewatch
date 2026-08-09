package com.rewatch.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.dto.WatchStatusRequest;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.WatchStatusService;

@RestController
@RequestMapping("/api/watch-status")
public class WatchStatusController {

    private final WatchStatusService watchStatusService;

    public WatchStatusController(WatchStatusService watchStatusService) {
        this.watchStatusService = watchStatusService;
    }

    @GetMapping("/{userId}")
    public Map<Long, String> getStatuses(@PathVariable Long userId, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        return watchStatusService.statusesFor(userId);
    }

    @PutMapping
    public ResponseEntity<?> setStatus(@Valid @RequestBody WatchStatusRequest req, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        try {
            watchStatusService.setStatus(req.getUserId(), req.getTitleId(), req.getStatus());
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
