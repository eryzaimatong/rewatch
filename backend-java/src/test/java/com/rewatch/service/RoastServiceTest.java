package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.Rating;
import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitNode;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;

/**
 * Covers RoastService.generate() — every roast line must trace to a real,
 * computed number (a rating average, a genre share, a rewatch count, a trait
 * extreme), the same "no fabricated content" discipline ArchetypeService and
 * TasteJourneyService already apply, just aimed at humor instead of a
 * straight read. ProfileService is real (Mockito can't mock concrete classes
 * here — see other tests in this package), subclassed to hand back a
 * controllable profile the same way FakeXService patterns do elsewhere.
 */
@ExtendWith(MockitoExtension.class)
class RoastServiceTest {

    @Mock private RatingRepository ratingRepo;
    @Mock private TitleRepository titleRepo;

    private static class FakeProfileService extends ProfileService {
        private final Map<Trait, TraitNode> profile;

        FakeProfileService(Map<Trait, TraitNode> profile) {
            super(null, null, null, null, null, null);
            this.profile = profile;
        }

        @Override
        public Map<Trait, TraitNode> currentProfile(Long userId) {
            return profile;
        }
    }

    private RoastService newRoastService(Map<Trait, TraitNode> profile) {
        return new RoastService(ratingRepo, titleRepo, new FakeProfileService(profile));
    }

    private Rating rating(long id, long titleId, int overall, int rewatch) {
        Rating r = new Rating();
        r.setId(id);
        r.setUserId(1L);
        r.setTitleId(titleId);
        r.setOverall(overall);
        r.setRewatch(rewatch == 0 ? null : rewatch);
        r.setCreatedAt(Instant.now());
        return r;
    }

    private Title titleWithGenre(long id, String genreIds) {
        Title t = new Title();
        t.setId(id);
        t.setGenreIds(genreIds);
        return t;
    }

    private Map<Trait, TraitNode> neutralProfile() {
        Map<Trait, TraitNode> m = new java.util.EnumMap<>(Trait.class);
        for (Trait t : Trait.values()) {
            m.put(t, new TraitNode(0.5, 0.9, 10, 0, Instant.now()));
        }
        return m;
    }

    @Test
    void belowRatingsFloorReturnsNull() {
        when(ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(1L))
                .thenReturn(List.of(rating(1, 100, 5, 0)));

        assertNull(newRoastService(neutralProfile()).generate(1L));
    }

    @Test
    void dominantGenreProducesAReceiptAndHeadline() {
        List<Rating> ratings = new ArrayList<>();
        for (long i = 0; i < 8; i++) {
            ratings.add(rating(i, i, 3, 0));
            when(titleRepo.findById(i)).thenReturn(java.util.Optional.of(titleWithGenre(i, "27"))); // Horror
        }
        when(ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(ratings);

        RoastService.Roast roast = newRoastService(neutralProfile()).generate(1L);

        assertTrue(roast.headline().toLowerCase(java.util.Locale.ROOT).contains("genre"));
        assertTrue(roast.receipts().stream().anyMatch(r -> r.contains("Horror")));
    }

    @Test
    void highFiveStarShareProducesAReceipt() {
        List<Rating> ratings = new ArrayList<>();
        for (long i = 0; i < 6; i++) {
            ratings.add(rating(i, i, 5, 0));
            when(titleRepo.findById(i)).thenReturn(java.util.Optional.of(titleWithGenre(i, "18")));
        }
        when(ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(ratings);

        RoastService.Roast roast = newRoastService(neutralProfile()).generate(1L);

        assertTrue(roast.receipts().stream().anyMatch(r -> r.contains("5 stars")));
    }

    @Test
    void highRewatchCountProducesAReceipt() {
        List<Rating> ratings = new ArrayList<>();
        for (long i = 0; i < 8; i++) {
            ratings.add(rating(i, i, 3, i < 6 ? 1 : 0));
            when(titleRepo.findById(i)).thenReturn(java.util.Optional.of(titleWithGenre(i, "18")));
        }
        when(ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(ratings);

        RoastService.Roast roast = newRoastService(neutralProfile()).generate(1L);

        assertTrue(roast.receipts().stream().anyMatch(r -> r.contains("rewatched")));
    }

    @Test
    void extremeTraitValueProducesAReceipt() {
        List<Rating> ratings = new ArrayList<>();
        for (long i = 0; i < 6; i++) {
            ratings.add(rating(i, i, 3, 0));
            when(titleRepo.findById(i)).thenReturn(java.util.Optional.of(titleWithGenre(i, "18")));
        }
        when(ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(ratings);

        Map<Trait, TraitNode> profile = neutralProfile();
        profile.put(Trait.INTENSITY, new TraitNode(0.95, 0.9, 10, 0, Instant.now()));

        RoastService.Roast roast = newRoastService(profile).generate(1L);

        assertTrue(roast.receipts().stream().anyMatch(r -> r.contains("Emotional Intensity")));
    }

    @Test
    void flatProfileStillProducesAPunchlineNotAnEmptyCard() {
        List<Rating> ratings = new ArrayList<>();
        for (long i = 0; i < 5; i++) {
            ratings.add(rating(i, i, 3, 0));
            when(titleRepo.findById(i)).thenReturn(java.util.Optional.of(titleWithGenre(i, String.valueOf(i))));
        }
        when(ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(ratings);

        RoastService.Roast roast = newRoastService(neutralProfile()).generate(1L);

        assertEquals(1, roast.receipts().size());
        assertTrue(roast.receipts().get(0).length() > 0);
    }

    @Test
    void neverReturnsMoreThanThreeReceipts() {
        List<Rating> ratings = new ArrayList<>();
        for (long i = 0; i < 10; i++) {
            ratings.add(rating(i, i, 5, i < 8 ? 1 : 0));
            when(titleRepo.findById(i)).thenReturn(java.util.Optional.of(titleWithGenre(i, "27")));
        }
        when(ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(ratings);

        Map<Trait, TraitNode> profile = neutralProfile();
        profile.put(Trait.INTENSITY, new TraitNode(0.95, 0.9, 10, 0, Instant.now()));

        RoastService.Roast roast = newRoastService(profile).generate(1L);

        assertTrue(roast.receipts().size() <= 3);
    }
}
