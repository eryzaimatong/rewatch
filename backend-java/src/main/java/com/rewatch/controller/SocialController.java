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
import com.rewatch.repository.UserRepository;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.AchievementService;
import com.rewatch.service.NotificationService;
import com.rewatch.service.ReviewService;
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
    private final NotificationService notificationService;
    private final UserRepository userRepo;
    private final AchievementService achievementService;
    private final ReviewService reviewService;

    public SocialController(SocialService socialService, NotificationService notificationService,
                            UserRepository userRepo, AchievementService achievementService,
                            ReviewService reviewService) {
        this.socialService = socialService;
        this.notificationService = notificationService;
        this.userRepo = userRepo;
        this.achievementService = achievementService;
        this.reviewService = reviewService;
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
        // Achievements on both sides of a follow depend on data this call is about
        // to change (the follower's following-count, the followee's follower-count)
        // — snapshot each before the write, diff after, same pattern as
        // RatingService.submit.
        Map<String, String> callerUnlockedBefore = achievementService.unlockedTitles(callerId);
        Map<String, String> followeeUnlockedBefore = achievementService.unlockedTitles(userId);
        try {
            Map<String, Object> result = socialService.follow(callerId, userId);
            // Composed here, not inside SocialService, so NotificationService (which
            // depends on SocialService for the DNA-match piggyback) doesn't create a
            // circular bean dependency by also being depended on by SocialService.
            userRepo.findById(callerId).ifPresent(caller ->
                    notificationService.notifyNewFollower(userId, callerId, caller.getUsername()));
            notifyNewlyUnlocked(callerId, callerUnlockedBefore);
            notifyNewlyUnlocked(userId, followeeUnlockedBefore);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    private void notifyNewlyUnlocked(Long userId, Map<String, String> unlockedBefore) {
        achievementService.unlockedTitles(userId).forEach((key, title) -> {
            if (!unlockedBefore.containsKey(key)) {
                notificationService.notifyAchievementUnlocked(userId, title);
            }
        });
    }

    @DeleteMapping("/follow/{userId}")
    public ResponseEntity<?> unfollow(@PathVariable Long userId, Authentication authentication) {
        Long callerId = requireAuth(authentication);
        return ResponseEntity.ok(socialService.unfollow(callerId, userId));
    }

    /** Find a specific person by username — the only discovery path before this was algorithmic (DNA matches/activity feed). */
    @GetMapping("/search")
    public List<UserSummaryDTO> search(@RequestParam String query, Authentication authentication) {
        Long callerId = requireAuth(authentication);
        return socialService.searchUsers(query, callerId);
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
        Long callerId = requireAuth(authentication);
        return reviewService.withInteractionCounts(socialService.reviews(userId, callerId, limit), callerId);
    }

    /** Self-only: the caller's own feed of what people they follow have been rating. */
    @GetMapping("/{userId}/activity-feed")
    public List<Map<String, Object>> activityFeed(@PathVariable Long userId,
                                                   @RequestParam(defaultValue = "20") int limit,
                                                   Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        return reviewService.withInteractionCounts(socialService.activityFeed(userId, limit), userId);
    }

    @GetMapping("/{userId}/lists")
    public List<Map<String, Object>> lists(@PathVariable Long userId, Authentication authentication) {
        Long callerId = requireAuth(authentication);
        return socialService.publicLists(userId, callerId);
    }

    /** Every public collection across every user, excluding the caller's own — the "Collections" browse surface. */
    @GetMapping("/collections/discover")
    public List<Map<String, Object>> discoverCollections(@RequestParam(defaultValue = "20") int limit,
                                                          Authentication authentication) {
        Long callerId = requireAuth(authentication);
        return socialService.discoverCollections(callerId, limit);
    }

    /** Self-only: collections the caller has chosen to follow. */
    @GetMapping("/collections/followed")
    public List<Map<String, Object>> followedCollections(Authentication authentication) {
        Long callerId = requireAuth(authentication);
        return socialService.followedCollections(callerId);
    }

    @PostMapping("/collections/{folderId}/follow")
    public ResponseEntity<?> followCollection(@PathVariable Long folderId, Authentication authentication) {
        Long callerId = requireAuth(authentication);
        try {
            socialService.followCollection(callerId, folderId);
            return ResponseEntity.ok(Map.of("status", "success", "following", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @DeleteMapping("/collections/{folderId}/follow")
    public ResponseEntity<?> unfollowCollection(@PathVariable Long folderId, Authentication authentication) {
        Long callerId = requireAuth(authentication);
        socialService.unfollowCollection(callerId, folderId);
        return ResponseEntity.ok(Map.of("status", "success", "following", false));
    }

    @PostMapping("/block/{userId}")
    public ResponseEntity<?> block(@PathVariable Long userId, Authentication authentication) {
        Long callerId = requireAuth(authentication);
        try {
            return ResponseEntity.ok(socialService.block(callerId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @DeleteMapping("/block/{userId}")
    public ResponseEntity<?> unblock(@PathVariable Long userId, Authentication authentication) {
        Long callerId = requireAuth(authentication);
        return ResponseEntity.ok(socialService.unblock(callerId, userId));
    }

    /** Self-only: the caller's own list of who they've blocked, for the Settings page. */
    @GetMapping("/blocked")
    public List<UserSummaryDTO> blocked(Authentication authentication) {
        Long callerId = requireAuth(authentication);
        return socialService.listBlocked(callerId);
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
