package com.rewatch.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.dto.UserSummaryDTO;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.SocialService;

/**
 * The social layer. Follow/unfollow always act as the authenticated caller —
 * followerId is never taken from the request, so there is no way to forge an
 * edge on someone else's behalf. Profile/reviews/lists reads require *a*
 * valid login (this app has no anonymous social browsing) but not that the
 * caller *be* the target — that is the whole point of a public profile.
 */
@RestController
@RequestMapping("/api/social")
public class SocialController {

    private final SocialService socialService;

    public SocialController(SocialService socialService) {
        this.socialService = socialService;
    }

    @GetMapping("/profile/{userId}")
    public ResponseEntity<?> profile(@PathVariable Long userId, Authentication authentication) {
        Long callerId = SecurityUtil.currentUserId(authentication);
        if (callerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Login required"));
        }
        Map<String, Object> profile = socialService.publicProfile(userId, callerId);
        if (profile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Unknown user " + userId));
        }
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/follow/{userId}")
    public ResponseEntity<?> follow(@PathVariable Long userId, Authentication authentication) {
        Long callerId = requireAuth(authentication);
        try {
            return ResponseEntity.ok(socialService.follow(callerId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @DeleteMapping("/follow/{userId}")
    public ResponseEntity<?> unfollow(@PathVariable Long userId, Authentication authentication) {
        Long callerId = requireAuth(authentication);
        return ResponseEntity.ok(socialService.unfollow(callerId, userId));
    }

    @GetMapping("/{userId}/followers")
    public List<UserSummaryDTO> followers(@PathVariable Long userId, Authentication authentication) {
        return socialService.followers(userId, SecurityUtil.currentUserId(authentication));
    }

    @GetMapping("/{userId}/following")
    public List<UserSummaryDTO> following(@PathVariable Long userId, Authentication authentication) {
        return socialService.following(userId, SecurityUtil.currentUserId(authentication));
    }

    /** Self-only: "people whose taste looks like mine," ranked from the caller's own vector. */
    @GetMapping("/dna-matches/{userId}")
    public List<UserSummaryDTO> dnaMatches(@PathVariable Long userId,
                                           @RequestParam(defaultValue = "6") int limit,
                                           Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        return socialService.dnaMatches(userId, limit);
    }

    @GetMapping("/{userId}/reviews")
    public List<Map<String, Object>> reviews(@PathVariable Long userId,
                                             @RequestParam(defaultValue = "20") int limit,
                                             Authentication authentication) {
        requireAuth(authentication);
        return socialService.reviews(userId, limit);
    }

    /** Self-only: the caller's own feed of what people they follow have been rating. */
    @GetMapping("/{userId}/activity-feed")
    public List<Map<String, Object>> activityFeed(@PathVariable Long userId,
                                                   @RequestParam(defaultValue = "20") int limit,
                                                   Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        return socialService.activityFeed(userId, limit);
    }

    @GetMapping("/{userId}/lists")
    public List<Map<String, Object>> lists(@PathVariable Long userId, Authentication authentication) {
        requireAuth(authentication);
        return socialService.publicLists(userId);
    }

    private Long requireAuth(Authentication authentication) {
        Long callerId = SecurityUtil.currentUserId(authentication);
        if (callerId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Login required");
        }
        return callerId;
    }
}
