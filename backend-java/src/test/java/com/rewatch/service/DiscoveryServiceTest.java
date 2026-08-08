package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.Rating;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;

/**
 * Covers selectForgottenRatings() — the date/rating threshold logic behind
 * the "Rediscover" row — the same way SocialServiceBlockTest covers
 * isBlocked()/follow() without needing the concrete ProfileService/
 * Recommender collaborators DiscoveryService also depends on. Neither is
 * touched by this method, so both are passed null here. The DTO-scoring
 * loop in rediscoverForgotten() itself has no dedicated test, matching
 * hiddenGems()/becauseYouLoved()/similarDna() — none of which are tested
 * either, since that glue is exercised through Recommender's own tests.
 */
@ExtendWith(MockitoExtension.class)
class DiscoveryServiceTest {

    @Mock private TitleRepository titleRepo;
    @Mock private RatingRepository ratingRepo;

    private DiscoveryService newService() {
        return new DiscoveryService(titleRepo, ratingRepo, null, null);
    }

    private Rating rating(long titleId, int overall, int daysAgo) {
        Rating r = new Rating();
        r.setUserId(1L);
        r.setTitleId(titleId);
        r.setOverall(overall);
        r.setCreatedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        return r;
    }

    @Test
    void oldHighRatingIsSurfaced() {
        when(ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(rating(10, 5, 60)));

        List<Rating> result = newService().selectForgottenRatings(1L, 6);

        assertEquals(1, result.size());
    }

    @Test
    void recentHighRatingIsExcluded() {
        when(ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(rating(10, 5, 1)));

        List<Rating> result = newService().selectForgottenRatings(1L, 6);

        assertTrue(result.isEmpty());
    }

    @Test
    void oldLowRatingIsExcluded() {
        when(ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(rating(10, 2, 60)));

        List<Rating> result = newService().selectForgottenRatings(1L, 6);

        assertTrue(result.isEmpty());
    }

    @Test
    void resultsSortedByRatingThenAge() {
        when(ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(1L))
                .thenReturn(List.of(rating(10, 4, 60), rating(20, 5, 45)));

        List<Rating> result = newService().selectForgottenRatings(1L, 6);

        assertEquals(2, result.size());
        assertEquals(20L, result.get(0).getTitleId());
    }
}
