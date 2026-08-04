package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.Rating;
import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitVector;
import com.rewatch.model.UserTrait;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.TraitEventRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.repository.UserTraitRepository;

/**
 * The core architectural claim of the taste model: the profile is a PURE
 * FUNCTION of the ordered rating log (see docs/CASE-STUDY.md, "why replay,
 * not mutation"). Replaying the same log twice must produce bit-identical
 * results — that's what makes a lexicon fix a recompute instead of a data
 * migration, and what makes re-rating trivially correct.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceReplayTest {

    @Mock private RatingRepository ratingRepo;
    @Mock private TitleRepository titleRepo;
    @Mock private UserTraitRepository userTraitRepo;
    @Mock private TraitEventRepository traitEventRepo;
    @Mock private UserRepository userRepo;

    private ProfileService newService() {
        return new ProfileService(ratingRepo, titleRepo, userTraitRepo, traitEventRepo, userRepo, new VectorEngine());
    }

    private Title titleWith(long id, double... traitValues) {
        Title t = new Title();
        t.setId(id);
        t.setTraitVector(TraitVector.of(traitValues));
        t.setFeatureConfidence(0.8);
        return t;
    }

    private Rating ratingOf(long id, Long userId, long titleId, int overall, Instant at) {
        Rating r = new Rating();
        r.setId(id);
        r.setUserId(userId);
        r.setTitleId(titleId);
        r.setOverall(overall);
        r.setChars(overall);
        r.setEnding(overall);
        r.setVisuals(overall);
        r.setStory(overall);
        r.setRewatch(overall);
        r.setCreatedAt(at);
        return r;
    }

    private void stubFixedLog(Long userId) {
        when(userRepo.findById(userId)).thenReturn(Optional.empty());
        doNothing().when(traitEventRepo).deleteByUserId(userId);

        Title titleA = titleWith(1L, 0.9, 0.1, 0.5, 0.5, 0.2, 0.1, 0.3, 0.8, 0.2, 0.9);
        Title titleB = titleWith(2L, 0.2, 0.8, 0.5, 0.6, 0.7, 0.6, 0.4, 0.2, 0.8, 0.3);

        List<Rating> log = List.of(
                ratingOf(101L, userId, 1L, 5, Instant.parse("2026-01-01T00:00:00Z")),
                ratingOf(102L, userId, 2L, 2, Instant.parse("2026-01-02T00:00:00Z")),
                ratingOf(103L, userId, 1L, 4, Instant.parse("2026-01-03T00:00:00Z"))
        );
        when(ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(userId)).thenReturn(log);
        when(titleRepo.findAllById(any())).thenReturn(List.of(titleA, titleB));
        when(userTraitRepo.findByUserId(userId)).thenReturn(List.of());
    }

    @Test
    void replayingTheSameRatingLogTwiceProducesIdenticalShifts() {
        Long userId = 99L;
        stubFixedLog(userId);
        ProfileService svc = newService();

        List<ProfileService.Shift> firstRun = svc.replay(userId);
        List<ProfileService.Shift> secondRun = svc.replay(userId);

        assertEquals(firstRun.size(), secondRun.size());
        for (int i = 0; i < firstRun.size(); i++) {
            ProfileService.Shift a = firstRun.get(i);
            ProfileService.Shift b = secondRun.get(i);
            assertEquals(a.trait(), b.trait());
            assertEquals(a.before(), b.before(), 1e-12, a.trait() + ": 'before' must match across replays");
            assertEquals(a.after(), b.after(), 1e-12, a.trait() + ": 'after' must match across replays");
            assertEquals(a.delta(), b.delta(), 1e-12, a.trait() + ": 'delta' must match across replays");
        }
    }

    @Test
    void replayingTheSameRatingLogTwiceWritesIdenticalPersistedProfile() {
        Long userId = 100L;
        stubFixedLog(userId);
        ProfileService svc = newService();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserTrait>> captor = ArgumentCaptor.forClass(List.class);

        svc.replay(userId);
        svc.replay(userId);

        org.mockito.Mockito.verify(userTraitRepo, org.mockito.Mockito.times(2)).saveAll(captor.capture());
        List<List<UserTrait>> allSaves = captor.getAllValues();
        List<UserTrait> firstSave = allSaves.get(0);
        List<UserTrait> secondSave = allSaves.get(1);

        assertFalse(firstSave.isEmpty(), "replay must persist a row per trait");
        assertEquals(Trait.count(), firstSave.size());
        assertEquals(firstSave.size(), secondSave.size());

        for (int i = 0; i < firstSave.size(); i++) {
            UserTrait a = firstSave.get(i);
            UserTrait b = secondSave.get(i);
            assertEquals(a.getTrait(), b.getTrait());
            assertEquals(a.getValue(), b.getValue(), 1e-12, a.getTrait() + ": persisted value must be deterministic");
            assertEquals(a.getConfidence(), b.getConfidence(), 1e-12, a.getTrait() + ": persisted confidence must be deterministic");
            assertEquals(a.getEvidenceCount(), b.getEvidenceCount(), a.getTrait() + ": evidence count must be deterministic");
        }
    }

    @Test
    void relevanceGateStopsAnUninformativeMovieFromDraggingEveryTraitToNeutral() {
        // The load-bearing term from docs/CASE-STUDY.md: a movie that says nothing
        // about a given axis (sits at 0.5 on it) must not move that axis at all,
        // regardless of the overall star rating.
        Long userId = 101L;
        when(userRepo.findById(userId)).thenReturn(Optional.empty());
        doNothing().when(traitEventRepo).deleteByUserId(userId);

        // This title is neutral (0.5) on every axis except FAMILY.
        double[] vals = new double[Trait.count()];
        java.util.Arrays.fill(vals, 0.5);
        vals[Trait.FAMILY.ordinal()] = 0.95;
        Title uninformativeExceptFamily = titleWith(5L, vals);

        Rating fiveStars = ratingOf(201L, userId, 5L, 5, Instant.parse("2026-01-01T00:00:00Z"));

        when(ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(userId)).thenReturn(List.of(fiveStars));
        when(titleRepo.findAllById(any())).thenReturn(List.of(uninformativeExceptFamily));
        when(userTraitRepo.findByUserId(userId)).thenReturn(List.of());

        ProfileService svc = newService();
        List<ProfileService.Shift> shifts = svc.replay(userId);

        for (ProfileService.Shift shift : shifts) {
            if (shift.trait() == Trait.FAMILY) {
                assertFalse(Math.abs(shift.delta()) < 1e-9, "FAMILY has real signal (0.95) and must move");
            } else {
                assertEquals(0.0, shift.delta(), 1e-9,
                        shift.trait() + ": a neutral (uninformative) axis on the rated movie must not move at all");
            }
        }
    }
}
