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
        assertEquals("Undecided Cinephile", result.archetype());
    }

    @Test
    void aSingleStandoutTraitGetsItsOwnSoloArchetypeInsteadOfTheGenericFallback() {
        // High romance, everything else neutral — no combo should qualify, but
        // this should NOT fall through to "Undecided Cinephile" either. This is
        // exactly the coverage gap the old 4-archetype version had: a profile
        // that's clearly leaning one direction still landed on one static
        // string for everyone who didn't clear a specific two-trait combo.
        ArchetypeService.Result result = service.classify(profile(Map.of(Trait.ROMANCE, 0.85)));
        assertEquals("Hopeless Romantic", result.archetype());
    }

    @Test
    void cozyNostalgicRequiresBothComfortAndNostalgia() {
        ArchetypeService.Result result = service.classify(
                profile(Map.of(Trait.COMFORT, 0.85, Trait.NOSTALGIA, 0.85)));
        assertEquals("Cozy Nostalgic", result.archetype());
    }

    @Test
    void doomScrollerIsHighIntensityLowHope() {
        ArchetypeService.Result result = service.classify(
                profile(Map.of(Trait.INTENSITY, 0.85, Trait.HOPE, 0.30)));
        assertEquals("Doom Scroller", result.archetype());
    }

    @Test
    void moreSpecificComboWinsOverASoloTraitMatch() {
        // Comfort alone would qualify for the solo "Comfort-Watch Curator"
        // fallback, but paired with high hope and low intensity it should hit
        // the more specific two-trait combo first — combos are checked before
        // any solo fallback is considered.
        ArchetypeService.Result result = service.classify(
                profile(Map.of(Trait.HOPE, 0.85, Trait.COMFORT, 0.85, Trait.INTENSITY, 0.20)));
        assertEquals("Golden Retriever Energy", result.archetype());
    }
}
