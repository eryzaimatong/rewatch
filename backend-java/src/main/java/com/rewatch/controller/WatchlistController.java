package com.rewatch.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.dto.WatchlistItemDTO;
import com.rewatch.dto.WatchlistRequests.AddItem;
import com.rewatch.dto.WatchlistRequests.CreateFolder;
import com.rewatch.dto.WatchlistRequests.MoveItem;
import com.rewatch.dto.WatchlistRequests.RenameFolder;
import com.rewatch.dto.WatchlistRequests.SetFolderCollaborative;
import com.rewatch.dto.WatchlistRequests.SetFolderVisibility;
import com.rewatch.model.WatchlistFolder;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.AchievementService;
import com.rewatch.service.NotificationService;
import com.rewatch.service.WatchlistService;

@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final AchievementService achievementService;
    private final NotificationService notificationService;

    public WatchlistController(WatchlistService watchlistService, AchievementService achievementService,
                               NotificationService notificationService) {
        this.watchlistService = watchlistService;
        this.achievementService = achievementService;
        this.notificationService = notificationService;
    }

    @GetMapping("/{userId}")
    public List<WatchlistItemDTO> listItems(@PathVariable Long userId, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        return watchlistService.listItems(userId);
    }

    @GetMapping("/{userId}/folders")
    public List<WatchlistFolder> listFolders(@PathVariable Long userId, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        return watchlistService.listFolders(userId);
    }

    @PostMapping("/folders")
    public WatchlistFolder createFolder(@Valid @RequestBody CreateFolder req, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        return watchlistService.createFolder(req.getUserId(), req.getName());
    }

    @PostMapping("/items")
    public ResponseEntity<?> addItem(@Valid @RequestBody AddItem req, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        Map<String, String> unlockedBefore = achievementService.unlockedTitles(req.getUserId());
        try {
            WatchlistItemDTO result = watchlistService.addItem(req);
            achievementService.unlockedTitles(req.getUserId()).forEach((key, title) -> {
                if (!unlockedBefore.containsKey(key)) {
                    notificationService.notifyAchievementUnlocked(req.getUserId(), title);
                }
            });
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<?> removeItem(@PathVariable Long itemId, @RequestParam Long userId,
                                        Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        boolean removed = watchlistService.removeItem(userId, itemId);
        if (!removed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Item not found"));
        }
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @PatchMapping("/folders/{folderId}/name")
    public ResponseEntity<?> renameFolder(@PathVariable Long folderId, @Valid @RequestBody RenameFolder req,
                                          Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        try {
            WatchlistFolder updated = watchlistService.renameFolder(req.getUserId(), folderId, req.getName());
            if (updated == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("status", "error", "message", "Folder not found"));
            }
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @DeleteMapping("/folders/{folderId}")
    public ResponseEntity<?> deleteFolder(@PathVariable Long folderId, @RequestParam Long userId,
                                          Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        boolean deleted = watchlistService.deleteFolder(userId, folderId);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Folder not found"));
        }
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    @PatchMapping("/folders/{folderId}/visibility")
    public ResponseEntity<?> setFolderVisibility(@PathVariable Long folderId,
                                                  @Valid @RequestBody SetFolderVisibility req,
                                                  Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        WatchlistFolder updated = watchlistService.setFolderVisibility(
                req.getUserId(), folderId, Boolean.TRUE.equals(req.getIsPublic()));
        if (updated == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Folder not found"));
        }
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/folders/{folderId}/collaborative")
    public ResponseEntity<?> setFolderCollaborative(@PathVariable Long folderId,
                                                     @Valid @RequestBody SetFolderCollaborative req,
                                                     Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        try {
            WatchlistFolder updated = watchlistService.setFolderCollaborative(
                    req.getUserId(), folderId, Boolean.TRUE.equals(req.getCollaborative()));
            if (updated == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("status", "error", "message", "Folder not found"));
            }
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PatchMapping("/items/{itemId}/folder")
    public ResponseEntity<?> moveItem(@PathVariable Long itemId, @Valid @RequestBody MoveItem req,
                                      Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        WatchlistItemDTO updated = watchlistService.moveItem(req.getUserId(), itemId, req.getFolderId());
        if (updated == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Item or folder not found"));
        }
        return ResponseEntity.ok(updated);
    }
}
