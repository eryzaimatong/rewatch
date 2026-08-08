package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitVector;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.UserRepository;

/**
 * Covers passesDealbreakers() directly — the hard-filter half of the
 * dealbreaker feature (see OnboardingServiceTest for the soft trait-nudge
 * half). Pure function, no repository interaction, so ProfileService/
 * ScoringService are passed null here the same way SocialServiceBlockTest
 * nulls out collaborators a given method under test never touches.
 */
@ExtendWith(MockitoExtension.class)
class RecommenderTest {

    @Mock private TitleRepository titleRepo;
    @Mock private RatingRepository ratingRepo;
    @Mock private UserRepository userRepo;

    private Recommender newRecommender() {
        return new Recommender(titleRepo, ratingRepo, userRepo, null, null);
    }

    private Title titleWith(Trait trait, double value) {
        Title t = new Title();
        t.setTraitVector(TraitVector.neutral().with(trait, value));
        return t;
    }

    private Title titleWith(Trait traitA, double valueA, Trait traitB, double valueB) {
        Title t = new Title();
        t.setTraitVector(TraitVector.neutral().with(traitA, valueA).with(traitB, valueB));
        return t;
    }

    private Title renderableTitle(String title, Integer voteCount) {
        Title t = new Title();
        t.setTitle(title);
        t.setSynopsis("A real synopsis.");
        t.setPoster("/poster.jpg");
        t.setVoteCount(voteCount);
        return t;
    }

    @Test
    void highIntensityTitleFailsExcessiveGore() {
        Title t = titleWith(Trait.INTENSITY, 0.9);
        assertFalse(newRecommender().passesDealbreakers(t, Set.of("excessive gore")));
    }

    @Test
    void lowIntensityTitlePassesExcessiveGore() {
        Title t = titleWith(Trait.INTENSITY, 0.3);
        assertTrue(newRecommender().passesDealbreakers(t, Set.of("excessive gore")));
    }

    @Test
    void highIntensityTitleFailsJumpScares() {
        Title t = titleWith(Trait.INTENSITY, 0.9);
        assertFalse(newRecommender().passesDealbreakers(t, Set.of("jump scares")));
    }

    @Test
    void bittersweetLowHopeTitleFailsSadEnding() {
        Title t = titleWith(Trait.BITTER, 0.85, Trait.HOPE, 0.2);
        assertFalse(newRecommender().passesDealbreakers(t, Set.of("sad ending")));
    }

    @Test
    void bittersweetButHopefulTitlePassesSadEnding() {
        // High BITTER alone isn't enough — HOPE has to be low too, or a
        // legitimately bittersweet-but-ultimately-hopeful title would be
        // wrongly excluded.
        Title t = titleWith(Trait.BITTER, 0.85, Trait.HOPE, 0.8);
        assertTrue(newRecommender().passesDealbreakers(t, Set.of("sad ending")));
    }

    @Test
    void bittersweetTitleFailsOpenEnding() {
        Title t = titleWith(Trait.BITTER, 0.7);
        assertFalse(newRecommender().passesDealbreakers(t, Set.of("open ending")));
    }

    @Test
    void bittersweetTitleFailsUnresolvedEnding() {
        Title t = titleWith(Trait.BITTER, 0.7);
        assertFalse(newRecommender().passesDealbreakers(t, Set.of("unresolved ending")));
    }

    @Test
    void slowPacingTitleFailsSlowStart() {
        Title t = titleWith(Trait.PACING, 0.8);
        assertFalse(newRecommender().passesDealbreakers(t, Set.of("slow start")));
    }

    @Test
    void fastPacingTitlePassesSlowStart() {
        Title t = titleWith(Trait.PACING, 0.2);
        assertTrue(newRecommender().passesDealbreakers(t, Set.of("slow start")));
    }

    @Test
    void unmappedDealbreakersNeverExcludeAnything() {
        // Love Triangle / Animal Death / Cheating have no per-title proxy —
        // they stay soft-nudge-only, never a hard filter.
        Title t = titleWith(Trait.ROMANCE, 0.95);
        assertTrue(newRecommender().passesDealbreakers(t, Set.of("love triangle", "animal death", "cheating")));
    }

    @Test
    void emptyDealbreakerSetPassesEverything() {
        Title t = titleWith(Trait.INTENSITY, 1.0);
        assertTrue(newRecommender().passesDealbreakers(t, Set.of()));
    }

    @Test
    void titleMustCrossOnlyOneSelectedDealbreakerToBeExcluded() {
        Title t = titleWith(Trait.INTENSITY, 0.9);
        assertFalse(newRecommender().passesDealbreakers(t, Set.of("slow start", "excessive gore")));
    }

    @Test
    void candidatePoolDropsTitlesWithNoSynopsisOrPosterUnconditionally() {
        Title noSynopsis = renderableTitle("No Synopsis", 500);
        noSynopsis.setSynopsis(null);
        Title blankPoster = renderableTitle("Blank Poster", 500);
        blankPoster.setPoster("  ");
        Title good = renderableTitle("Good", 500);
        when(titleRepo.findAll()).thenReturn(List.of(noSynopsis, blankPoster, good));

        List<Title> pool = newRecommender().candidatePool(24);

        assertEquals(1, pool.size());
        assertEquals("Good", pool.get(0).getTitle());
    }

    @Test
    void candidatePoolPrefersWellKnownTitlesWhenEnoughExist() {
        List<Title> all = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            all.add(renderableTitle("well-known-" + i, 500));
        }
        all.add(renderableTitle("obscure", 3));
        when(titleRepo.findAll()).thenReturn(all);

        List<Title> pool = newRecommender().candidatePool(24);

        assertEquals(100, pool.size());
        assertTrue(pool.stream().noneMatch(t -> "obscure".equals(t.getTitle())));
    }

    @Test
    void candidatePoolFallsBackToEveryRenderableTitleWhenTooFewAreWellKnown() {
        // Only 2 well-known titles exist against a limit of 24 (needs 72 to
        // trust the vote-count bias) — a thin catalog (e.g. anime/kdrama right
        // now) must never be starved by a threshold tuned for a bigger one.
        Title wellKnown = renderableTitle("well-known", 500);
        Title obscure = renderableTitle("obscure", 3);
        Title noVotesYet = renderableTitle("no-votes-yet", null);
        when(titleRepo.findAll()).thenReturn(List.of(wellKnown, obscure, noVotesYet));

        List<Title> pool = newRecommender().candidatePool(24);

        assertEquals(3, pool.size());
    }
}
