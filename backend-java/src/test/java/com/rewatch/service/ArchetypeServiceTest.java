package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.rewatch.model.Trait;
import com.rewatch.model.TraitNode;

/**
 * Only the routing logic is under test here (which combo wins, and that the
 * solo-trait fallback actually engages instead of one static string for every
 * "balanced" profile) — the exact wording of each archetype/blurb is a
 * product/copy decision, not a behavior contract worth pinning down char for
 * char.
 */
class ArchetypeServiceTest {

    private final ArchetypeService service = new ArchetypeService();

    private Map<Trait, TraitNode> profile(Map<Trait, Double> overrides) {
        Map<Trait, TraitNode> profile = new EnumMap<>(Trait.class);
        for (Trait t : Trait.values()) {
            double val = overrides.getOrDefault(t, 0.5);
            profile.put(t, new TraitNode(val, 0.8, 0, 10, Instant.now()));
        }
        return profile;
    }

    @Test
    void allNeutralTraitsFallsBackToUndecided() {
        ArchetypeService.Result result = service.classify(profile(Map.of()));
        assertEquals(ArchetypeService.UNDECIDED, result.archetype());
    }

    @Test
    void aSingleStandoutTraitGetsItsOwnSoloArchetypeInsteadOfTheGenericFallback() {
        // High romance, everything else neutral — no combo should qualify, but
        // this should NOT fall through to the undecided fallback either. This is
        // exactly the coverage gap the old 4-archetype version had: a profile
        // that's clearly leaning one direction still landed on one static
        // string for everyone who didn't clear a specific two-trait combo.
        ArchetypeService.Result result = service.classify(profile(Map.of(Trait.ROMANCE, 0.85)));
        assertEquals("Professional Yearner", result.archetype());
    }

    @Test
    void comfortPlusNostalgiaIsTheComfortArchetype() {
        ArchetypeService.Result result = service.classify(
                profile(Map.of(Trait.COMFORT, 0.85, Trait.NOSTALGIA, 0.85)));
        assertEquals("Brain Smooth, Vibes Immaculate", result.archetype());
    }

    @Test
    void highIntensityLowHopeIsTheHorrorFan() {
        ArchetypeService.Result result = service.classify(
                profile(Map.of(Trait.INTENSITY, 0.85, Trait.HOPE, 0.30)));
        assertEquals("Sleep Paralysis Demon's BFF", result.archetype());
    }

    @Test
    void moreSpecificComboWinsOverASoloTraitMatch() {
        // Comfort alone would qualify for the solo comfort archetype
        // fallback, but paired with high hope and low intensity it should hit
        // the more specific two-trait combo first — combos are checked before
        // any solo fallback is considered.
        ArchetypeService.Result result = service.classify(
                profile(Map.of(Trait.HOPE, 0.85, Trait.COMFORT, 0.85, Trait.INTENSITY, 0.20)));
        assertEquals("Golden Retriever Energy", result.archetype());
    }

    @Test
    void everyArchetypeAndBlurbIsNonBlankAndFitsAShareCard() {
        // The share card and profile hero truncate nothing, so keep both strings
        // short enough to survive a screenshot.
        for (ArchetypeService.Result r : service.allResultsForReview()) {
            org.junit.jupiter.api.Assertions.assertFalse(r.archetype().isBlank());
            org.junit.jupiter.api.Assertions.assertFalse(r.blurb().isBlank());
            org.junit.jupiter.api.Assertions.assertTrue(r.archetype().length() <= 40, r.archetype());
            org.junit.jupiter.api.Assertions.assertTrue(r.blurb().length() <= 170, r.blurb());
        }
    }

    @Test
    void romanceWithIntensityIsEnemiesToLovers() {
        ArchetypeService.Result result = service.classify(
                profile(Map.of(Trait.ROMANCE, 0.85, Trait.INTENSITY, 0.70)));
        assertEquals("Enemies-to-Lovers Truther", result.archetype());
    }
}
