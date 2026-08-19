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

import com.rewatch.dto.MovieDTO;
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

    // --- applyBrowseFilters: the actual fix for "combining vibe/genre/
    // language filters returns zero results" — genre/language/vibe used to
    // only ever filter the ~30 already-ranked titles an unfiltered feed
    // returned; this applies them to the full candidate pool instead. ---

    private Title titleWithGenres(String title, String genreIds) {
        Title t = renderableTitle(title, 500);
        t.setGenreIds(genreIds);
        return t;
    }

    private Title titleWithLanguage(String title, String language) {
        Title t = renderableTitle(title, 500);
        t.setOriginalLanguage(language);
        return t;
    }

    @Test
    void genreFilterKeepsOnlyTitlesResolvingToThatGenreName() {
        Title horror = titleWithGenres("Horror Movie", "27");
        Title comedy = titleWithGenres("Comedy Movie", "35");
        List<Title> result = newRecommender().applyBrowseFilters(List.of(horror, comedy), "Horror", null, null);

        assertEquals(1, result.size());
        assertEquals("Horror Movie", result.get(0).getTitle());
    }

    @Test
    void languageFilterKeepsOnlyMatchingOriginalLanguage() {
        Title en = titleWithLanguage("English Movie", "en");
        Title ko = titleWithLanguage("Korean Movie", "ko");
        List<Title> result = newRecommender().applyBrowseFilters(List.of(en, ko), null, "ko", null);

        assertEquals(1, result.size());
        assertEquals("Korean Movie", result.get(0).getTitle());
    }

    @Test
    void vibeFilterMapsToARealTraitThresholdNotTextMatching() {
        // The old bug's exact shape: a title whose reasons/genres/title text
        // never mentions the word "romance" at all must still match the
        // Romance vibe if its actual romance trait value is high.
        Title romantic = renderableTitle("Some Random Title", 500);
        romantic.setTraitVector(TraitVector.neutral().with(Trait.ROMANCE, 0.9));
        Title notRomantic = renderableTitle("Another Title", 500);
        notRomantic.setTraitVector(TraitVector.neutral().with(Trait.ROMANCE, 0.2));

        List<Title> result = newRecommender().applyBrowseFilters(List.of(romantic, notRomantic), null, null, "Romance");

        assertEquals(1, result.size());
        assertEquals("Some Random Title", result.get(0).getTitle());
    }

    @Test
    void allThreeFiltersCanCombineWithoutTriviallyEmptyingASixThousandTitleCatalog() {
        // The reported bug: Romance (vibe) + Western (genre) + English
        // (language) selected together returned zero. Simulates the same
        // shape at a small scale — one title that genuinely satisfies all
        // three must still survive being filtered by all three at once.
        Title matches = titleWithGenres("The One", "37"); // Western
        matches.setOriginalLanguage("en");
        matches.setTraitVector(TraitVector.neutral().with(Trait.ROMANCE, 0.9));

        Title wrongGenre = titleWithGenres("Wrong Genre", "27"); // Horror
        wrongGenre.setOriginalLanguage("en");
        wrongGenre.setTraitVector(TraitVector.neutral().with(Trait.ROMANCE, 0.9));

        List<Title> result = newRecommender()
                .applyBrowseFilters(List.of(matches, wrongGenre), "Western", "en", "Romance");

        assertEquals(1, result.size());
        assertEquals("The One", result.get(0).getTitle());
    }

    @Test
    void allOrNullFiltersAreANoOp() {
        Title a = renderableTitle("A", 500);
        Title b = renderableTitle("B", 500);
        List<Title> result = newRecommender().applyBrowseFilters(List.of(a, b), "All", null, "All");

        assertEquals(2, result.size());
    }

    // --- coldStartSafeReorder: the fix for "with one rating, Tonight's Pick
    // was Planet Earth" — low INTENSITY/BITTER and a strong vote average
    // describe a calm nature documentary just as well as a genuinely
    // comforting scripted pick, so the cold-start safety net was promoting
    // (or leaving in place) exactly the wrong kind of first impression. ---

    private MovieDTO safeCandidate(String title, List<String> genres) {
        MovieDTO dto = new MovieDTO();
        dto.setTitle(title);
        dto.setGenres(genres);
        dto.setVoteAverage(8.5);
        dto.setStoryVector(TraitVector.neutral()
                .with(Trait.INTENSITY, 0.1)
                .with(Trait.BITTER, 0.1)
                .toKeyedMap());
        return dto;
    }

    @Test
    void documentaryLeadingAColdProfileIsDemotedInFavorOfAScriptedSafePick() {
        MovieDTO doc = safeCandidate("Planet Earth", List.of("Documentary"));
        MovieDTO scripted = safeCandidate("A Cozy Comedy", List.of("Comedy"));

        List<MovieDTO> result = newRecommender().coldStartSafeReorder(List.of(doc, scripted), 0.2);

        assertEquals("A Cozy Comedy", result.get(0).getTitle());
    }

    @Test
    void documentaryStaysPutWhenNothingElseInTheLookaheadQualifies() {
        // Nothing better to promote to — same "leave it alone" fallback the
        // existing safety net already uses when the lookahead is empty.
        MovieDTO doc = safeCandidate("Planet Earth", List.of("Documentary"));

        List<MovieDTO> result = newRecommender().coldStartSafeReorder(List.of(doc), 0.2);

        assertEquals("Planet Earth", result.get(0).getTitle());
    }

    @Test
    void reorderIsANoOpOnceProfileConfidenceIsNoLongerCold() {
        MovieDTO doc = safeCandidate("Planet Earth", List.of("Documentary"));
        MovieDTO scripted = safeCandidate("A Cozy Comedy", List.of("Comedy"));

        List<MovieDTO> result = newRecommender().coldStartSafeReorder(List.of(doc, scripted), 0.9);

        assertEquals("Planet Earth", result.get(0).getTitle());
    }
}
