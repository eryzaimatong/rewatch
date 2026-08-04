package com.rewatch.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The canonical storytelling vocabulary. This is the single source of truth for
 * what dimensions Re:Watch reasons about.
 *
 * Before this enum existed the codebase carried five mutually incompatible trait
 * vocabularies (a 5-axis TasteDNA entity, a 6-axis StoryVector, a 10-axis
 * FeatureVector, ten hardcoded string keys in TasteDNAController, and a
 * StoryPrint-10 CSV inherited from the old Python backend). Scores computed in
 * one vocabulary were displayed next to scores computed in another, which is why
 * the same title could show two different match percentages.
 *
 * Display labels are authoritative here rather than in the frontend, so the UI
 * cannot drift from the model.
 *
 * NOTE: persisted as @Enumerated(STRING), never ORDINAL. Reordering the constants
 * below is safe for the database but DOES change array layout, so anything that
 * serialises a raw double[] must go through {@link TraitVector}.
 */
public enum Trait {

    FAMILY("family", "Found Family",
            "the ensemble bonds you look for",
            "lonelier than your usual"),

    NOSTALGIA("nostalgia", "Nostalgia & Memory",
            "steeped in memory, like you prefer",
            "more present-tense than you tend to like"),

    GROWTH("growth", "Character Growth",
            "characters change, which you care about",
            "the characters end where they started"),

    PACING("pacing", "Slow Burn Pacing",
            "takes its time, the way you like it",
            "moves faster than your usual pace"),

    HUMOR("humor", "Humor & Lightheartedness",
            "keeps a light touch",
            "much heavier than your usual"),

    ROMANCE("romance", "Romance",
            "the romantic centre you gravitate to",
            "little romance to hold onto"),

    INTENSITY("intensity", "Emotional Intensity",
            "hits at the intensity you seek out",
            "more intense than you usually sit with"),

    HOPE("hope", "Hopeful Payoff",
            "earns its hopeful ending",
            "offers less hope than you usually want"),

    BITTER("bitter", "Bittersweet Drama",
            "leaves things unresolved, like you prefer",
            "tidier than the bittersweet you favour"),

    COMFORT("comfort", "Comfort & Warmth",
            "warm and low-anxiety, your baseline",
            "colder than your usual comfort watch");

    /** Stable wire key used in JSON and in the database. Never change these. */
    private final String key;
    private final String label;
    private final String driverBlurb;
    private final String tensionBlurb;

    private static final Map<String, Trait> BY_KEY;

    static {
        Map<String, Trait> m = new HashMap<>();
        for (Trait t : values()) {
            m.put(t.key, t);
        }
        BY_KEY = Collections.unmodifiableMap(m);
    }

    Trait(String key, String label, String driverBlurb, String tensionBlurb) {
        this.key = key;
        this.label = label;
        this.driverBlurb = driverBlurb;
        this.tensionBlurb = tensionBlurb;
    }

    public String key() { return key; }

    public String label() { return label; }

    /** Phrase used when this trait pushed the score up. */
    public String driverBlurb() { return driverBlurb; }

    /** Phrase used when this trait pushed the score down. */
    public String tensionBlurb() { return tensionBlurb; }

    public static Trait fromKey(String key) {
        if (key == null) {
            return null;
        }
        return BY_KEY.get(key.trim().toLowerCase());
    }

    public static int count() {
        return values().length;
    }
}
