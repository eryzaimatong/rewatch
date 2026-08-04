package com.rewatch.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.model.Notification;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/{userId}")
    public List<Notification> list(@PathVariable Long userId, @RequestParam(defaultValue = "20") int limit,
                                   Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        return notificationService.list(userId, limit);
    }

    @GetMapping("/{userId}/unread-count")
    public Map<String, Object> unreadCount(@PathVariable Long userId, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        return Map.of("count", notificationService.unreadCount(userId));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, @RequestParam Long userId,
                                      Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        notificationService.markRead(userId, id);
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @PostMapping("/{userId}/read-all")
    public ResponseEntity<?> markAllRead(@PathVariable Long userId, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        notificationService.markAllRead(userId);
        return ResponseEntity.ok(Map.of("status", "success"));
    }
}
