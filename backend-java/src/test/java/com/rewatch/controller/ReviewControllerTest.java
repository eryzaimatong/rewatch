package com.rewatch.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.rewatch.model.Rating;
import com.rewatch.model.ReviewComment;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.security.RateLimiterService;
import com.rewatch.service.NotificationService;
import com.rewatch.service.ReviewService;

/**
 * addComment() previously only bounded comment *content* (length, blocked-
 * user pairs, both enforced in ReviewService) — nothing bounded *volume*.
 * See SocialControllerTest's class comment for why every collaborator here
 * is a hand-written subclass rather than @Mock.
 */
class ReviewControllerTest {

    private static class FakeReviewService extends ReviewService {
        int addCommentCalls = 0;

        FakeReviewService() { super(null, null, null, null, null); }

        @Override
        public ReviewComment addComment(Long callerId, Long ratingId, String body, boolean hasSpoilers) {
            addCommentCalls++;
            ReviewComment c = new ReviewComment(ratingId, callerId, body, Instant.now());
            c.setId((long) addCommentCalls);
            return c;
        }

        @Override
        public Rating ratingFor(Long ratingId) {
            // null short-circuits ReviewController.notifyReviewOwner before it
            // touches notificationService/userRepo/titleRepo — none of those
            // need stubbing for this test.
            return null;
        }
    }

    private Authentication authAs(long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of());
    }

    @Test
    void addCommentIsRateLimitedAfterTooManyCallsInAnHour() {
        FakeReviewService reviewService = new FakeReviewService();
        ReviewController controller = new ReviewController(
                reviewService, (NotificationService) null, (UserRepository) null, (TitleRepository) null,
                new RateLimiterService());

        Authentication auth = authAs(1L);
        ResponseEntity<?> last = null;
        // MAX_COMMENTS_PER_HOUR is 30 — the 31st call must be rejected
        // before ever reaching ReviewService.addComment.
        for (int i = 0; i < 31; i++) {
            last = controller.addComment(500L, Map.of("body", "nice review " + i), auth);
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, last.getStatusCode());
        assertEquals(30, reviewService.addCommentCalls, "the 31st call should never reach the service");
    }

    @Test
    void addCommentSucceedsNormallyUnderTheLimit() {
        FakeReviewService reviewService = new FakeReviewService();
        ReviewController controller = new ReviewController(
                reviewService, (NotificationService) null, (UserRepository) null, (TitleRepository) null,
                new RateLimiterService());

        ResponseEntity<?> result = controller.addComment(500L, Map.of("body", "nice review"), authAs(1L));

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(1, reviewService.addCommentCalls);
    }
}
