package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.Rating;
import com.rewatch.model.Title;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;

/**
 * Covers selectForgottenRatings() — the date/rating threshold logic behind
 * the "Rediscover" row — and hiddenGemCandidates() — the filtering behind
 * "Hidden Gems" — the same way SocialServiceBlockTest covers isBlocked()/
 * follow() without needing the concrete ProfileService/Recommender
 * collaborators DiscoveryService also depends on. Neither method touches
 * them, so both are passed null here. The DTO-scoring loop itself (in
 * hiddenGems(), rediscoverForgotten(), becauseYouLoved(), similarDna()) has
 * no dedicated test — that glue is exercised through Recommender's own
 * tests.
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

    // --- hiddenGemCandidates(): the fix for "Hidden Gems surfaced a 1928
    // Soviet film and a 1936 Hungarian film" — normalisedQuality() is a bare
    // voteAverage/10 with no regard to how many votes it's an average of, so
    // a handful of enthusiastic votes on an obscure old title cleared the old
    // quality bar as easily as a real audience would. ---

    private long nextTitleId = 1;

    private Title title(String name, double popularity, double voteAverage, int voteCount, int year) {
        Title t = new Title();
        t.setId(nextTitleId++);
        t.setTitle(name);
        t.setSynopsis("A real synopsis.");
        t.setPoster("/poster.jpg");
        t.setPopularity(popularity);
        t.setVoteAverage(voteAverage);
        t.setVoteCount(voteCount);
        t.setYear(year);
        return t;
    }

    @Test
    void lowVoteCountObscureTitleIsExcludedRegardlessOfHighAverage() {
        // Exactly the reported bug's shape: 3 votes averaging 9/10 clears any
        // average-based quality bar but reflects no real audience.
        Title obscure = title("Obscure Classic", 0.5, 9.0, 3, 1928);
        Title real = title("Real Hidden Gem", 0.5, 7.5, 200, 2015);

        List<Title> result = newService().hiddenGemCandidates(
                List.of(obscure, real), Set.of(), true, 4);

        assertEquals(1, result.size());
        assertEquals("Real Hidden Gem", result.get(0).getTitle());
    }

    @Test
    void oldTitleExcludedForUserWhoHasNotEarnedDeepCutsYet() {
        Title old = title("Old But Well-Voted", 0.5, 8.0, 500, Year.now().getValue() - 40);
        Title recent = title("Recent Gem", 0.5, 7.8, 300, Year.now().getValue() - 2);

        List<Title> result = newService().hiddenGemCandidates(
                List.of(old, recent), Set.of(), false, 4);

        assertEquals(1, result.size());
        assertEquals("Recent Gem", result.get(0).getTitle());
    }

    @Test
    void oldTitleIncludedForUserWhoHasEarnedDeepCuts() {
        Title old = title("Old But Well-Voted", 0.5, 8.0, 500, Year.now().getValue() - 40);

        List<Title> result = newService().hiddenGemCandidates(
                List.of(old), Set.of(), true, 4);

        assertEquals(1, result.size());
    }

    @Test
    void ratedTitlesAreExcludedEvenIfOtherwiseEligible() {
        Title t = title("Already Rated", 0.5, 8.0, 300, 2015);
        t.setId(99L);

        List<Title> result = newService().hiddenGemCandidates(
                List.of(t), Set.of(99L), true, 4);

        assertTrue(result.isEmpty());
    }
}
