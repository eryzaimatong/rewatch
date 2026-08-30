package com.rewatch.controller;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.model.Rating;
import com.rewatch.model.ReviewComment;
import com.rewatch.model.Title;
import com.rewatch.model.User;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.security.RateLimiterService;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.NotificationService;
import com.rewatch.service.ReviewService;

/**
 * Likes and comments on a review. Notification composition happens here, not
 * in ReviewService — same reasoning as SocialController.follow: keeps
 * ReviewService free of a NotificationService dependency it would otherwise
 * only ever use for this one thing.
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final NotificationService notificationService;
    private final UserRepository userRepo;
    private final TitleRepository titleRepo;
    private final RateLimiterService rateLimiter;

    // Generous for a real conversation, tight enough to blunt a comment-spam
    // script — nothing here previously bounded volume at all, only content
    // (ReviewService already caps length and blocks blocked-user pairs).
    private static final int MAX_COMMENTS_PER_HOUR = 30;

    public ReviewController(ReviewService reviewService, NotificationService notificationService,
                            UserRepository userRepo, TitleRepository titleRepo, RateLimiterService rateLimiter) {
        this.reviewService = reviewService;
        this.notificationService = notificationService;
        this.userRepo = userRepo;
        this.titleRepo = titleRepo;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/{ratingId}/like")
    public ResponseEntity<?> toggleLike(@PathVariable Long ratingId, Authentication authentication) {
        Long callerId = requireAuth(authentication);
        try {
            ReviewService.LikeResult result = reviewService.toggleLike(callerId, ratingId);
            if (result.liked()) {
                notifyReviewOwner(ratingId, callerId,
                        (owner, liker, title) -> notificationService.notifyReviewLiked(owner, callerId, liker, title));
            }
            return ResponseEntity.ok(Map.of("status", "success", "liked", result.liked(), "likeCount", result.likeCount()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/{ratingId}/comments")
    public List<Map<String, Object>> listComments(@PathVariable Long ratingId, Authentication authentication) {
        Long callerId = SecurityUtil.currentUserId(authentication);
        return reviewService.listComments(ratingId, callerId);
    }

    @PostMapping("/{ratingId}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long ratingId, @RequestBody Map<String, Object> body,
                                        Authentication authentication) {
        Long callerId = requireAuth(authentication);
        String key = "comment:" + callerId;
        if (!rateLimiter.allow(key, MAX_COMMENTS_PER_HOUR, Duration.ofHours(1))) {
            return rateLimiter.tooManyRequests(key, Duration.ofHours(1), "Too many comments posted.");
        }
        Object rawBody = body.get("body");
        if (!(rawBody instanceof String commentText)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", "'body' must be a string"));
        }
        try {
            boolean hasSpoilers = Boolean.TRUE.equals(body.get("hasSpoilers"));
            ReviewComment comment = reviewService.addComment(callerId, ratingId, commentText, hasSpoilers);
            notifyReviewOwner(ratingId, callerId,
                    (owner, commenter, title) -> notificationService.notifyReviewCommented(owner, callerId, commenter, title));
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "id", comment.getId(),
                    "body", comment.getBody(),
                    "createdAt", comment.getCreatedAt(),
                    "hasSpoilers", comment.isHasSpoilers()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId, Authentication authentication) {
        Long callerId = requireAuth(authentication);
        try {
            reviewService.deleteComment(callerId, commentId);
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @FunctionalInterface
    private interface NotifyFn {
        void notify(Long ownerId, String actorUsername, String titleName);
    }

    /** Skips self-notification (liking/commenting on your own review) and any side that can no longer be resolved. */
    private void notifyReviewOwner(Long ratingId, Long callerId, NotifyFn notify) {
        Rating rating = reviewService.ratingFor(ratingId);
        if (rating == null || rating.getUserId().equals(callerId)) {
            return;
        }
        User caller = userRepo.findById(callerId).orElse(null);
        Title title = titleRepo.findById(rating.getTitleId()).orElse(null);
        if (caller == null || title == null) {
            return;
        }
        notify.notify(rating.getUserId(), caller.getUsername(), title.getTitle());
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
