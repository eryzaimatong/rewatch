package com.rewatch.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.rewatch.repository.UserRepository;
import com.rewatch.security.RateLimiterService;
import com.rewatch.service.AchievementService;
import com.rewatch.service.NotificationService;
import com.rewatch.service.ReviewService;
import com.rewatch.service.SocialService;

/**
 * follow() had no rate limit at all until now — nothing bounded how many
 * times a script could call it in a loop. Every collaborator here is a
 * hand-written subclass rather than @Mock: this JDK/Mockito combination
 * can't byte-buddy-instrument concrete classes (see TmdbServiceSearchTest's
 * class comment for the same constraint), and none of SocialController's
 * dependencies are interfaces. RateLimiterService itself is real, not
 * stubbed — it's cheap to construct (in-memory, no dependencies) and it's
 * the actual thing under test.
 */
class SocialControllerTest {

    private static class FakeAchievementService extends AchievementService {
        FakeAchievementService() { super(null, null, null, null, null, null); }

        @Override
        public Map<String, String> unlockedTitles(Long userId) { return Map.of(); }
    }

    private static class FakeSocialService extends SocialService {
        int followCalls = 0;

        FakeSocialService() { super(null, null, null, null, null, null, null, null, null, null); }

        @Override
        public Map<String, Object> follow(Long followerId, Long followeeId) {
            followCalls++;
            // isNewFollow=false keeps the controller from touching
            // userRepo/notificationService at all for this test's purposes.
            return Map.of("status", "success", "following", true, "isNewFollow", false);
        }
    }

    // SecurityUtil.currentUserId requires the principal to actually be a
    // Long — the JwtAuthFilter sets it up this way in the real request path.
    private Authentication authAs(long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of());
    }

    @Test
    void followIsRateLimitedAfterTooManyCallsInAnHour() {
        FakeSocialService socialService = new FakeSocialService();
        SocialController controller = new SocialController(
                socialService, null, (UserRepository) null, new FakeAchievementService(), (ReviewService) null,
                new RateLimiterService());

        Authentication auth = authAs(1L);
        ResponseEntity<?> last = null;
        // MAX_FOLLOWS_PER_HOUR is 60 — the 61st call in the same hour must
        // be rejected before ever reaching SocialService.follow.
        for (int i = 0; i < 61; i++) {
            last = controller.follow((long) (100 + i), auth);
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, last.getStatusCode());
        assertEquals(60, socialService.followCalls, "the 61st call should never reach the service");
    }

    @Test
    void followSucceedsNormallyUnderTheLimit() {
        FakeSocialService socialService = new FakeSocialService();
        SocialController controller = new SocialController(
                socialService, null, (UserRepository) null, new FakeAchievementService(), (ReviewService) null,
                new RateLimiterService());

        ResponseEntity<?> result = controller.follow(2L, authAs(1L));

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(1, socialService.followCalls);
    }
}
