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
 *
 * Each trait carries FOUR blurbs, not two. A "driver" (this axis pushed the score
 * up) can happen because the movie sits HIGH on this axis and the user wants that,
 * OR because the movie sits LOW and the user wants that too — those are opposite
 * facts about the movie and need opposite sentences. Picking blurbs purely off
 * driver-vs-tension (as this used to do) meant a low-comfort, high-anxiety drama
 * like a war tragedy could get labelled "warm and low-anxiety, your baseline"
 * whenever a low-comfort-seeking user matched it — technically a correct match,
 * described in a sentence that flatly contradicts what the movie actually is.
 * The fix isn't the embeddings (a war drama already scores low comfort, correctly,
 * via GenreLexicon's negative Crime/Thriller/War weights) — it's that the sentence
 * needs to know which direction the movie's own value actually sits in.
 */
public enum Trait {

    FAMILY("family", "Found Family",
            "the ensemble bonds you look for",
            "keeps focus on one or two people, like you prefer",
            "leans on ensemble warmth more than you usually want",
            "lonelier than your usual"),

    NOSTALGIA("nostalgia", "Nostalgia & Memory",
            "steeped in memory, like you prefer",
            "stays grounded in the present, the way you like",
            "leans on nostalgia more than you usually want",
            "more present-tense than you tend to like"),

    GROWTH("growth", "Character Growth",
            "characters change, which you care about",
            "characters stay steady, which suits you",
            "characters transform more than you usually look for",
            "the characters end where they started"),

    PACING("pacing", "Slow Burn Pacing",
            "takes its time, the way you like it",
            "keeps things moving, which suits you",
            "moves slower than you usually have patience for",
            "moves faster than your usual pace"),

    HUMOR("humor", "Humor & Lightheartedness",
            "keeps a light touch",
            "stays serious, which suits your mood",
            "lighter in tone than you usually want",
            "much heavier than your usual"),

    ROMANCE("romance", "Romance",
            "the romantic centre you gravitate to",
            "keeps romance in the background, like you prefer",
            "more romance-driven than you usually want",
            "little romance to hold onto"),

    INTENSITY("intensity", "Emotional Intensity",
            "hits at the intensity you seek out",
            "stays gentle, the intensity you're comfortable with",
            "more intense than you usually sit with",
            "calmer than the intensity you're usually drawn to"),

    HOPE("hope", "Hopeful Payoff",
            "earns its hopeful ending",
            "sits with a harder ending, which you can take",
            "wraps up brighter than you usually want",
            "offers less hope than you usually want"),

    BITTER("bitter", "Bittersweet Drama",
            "leaves things unresolved, like you prefer",
            "resolves cleanly, the way you like",
            "more bittersweet than you usually want",
            "tidier than the bittersweet you favour"),

    COMFORT("comfort", "Comfort & Warmth",
            "warm and low-anxiety, your baseline",
            "tense in a way you don't mind",
            "cozier than you're looking for right now",
            "colder than your usual comfort watch");

    /** Stable wire key used in JSON and in the database. Never change these. */
    private final String key;
    private final String label;
    private final String driverBlurbHigh;
    private final String driverBlurbLow;
    private final String tensionBlurbHigh;
    private final String tensionBlurbLow;

    private static final Map<String, Trait> BY_KEY;

    static {
        Map<String, Trait> m = new HashMap<>();
        for (Trait t : values()) {
            m.put(t.key, t);
        }
        BY_KEY = Collections.unmodifiableMap(m);
    }

    Trait(String key, String label, String driverBlurbHigh, String driverBlurbLow,
          String tensionBlurbHigh, String tensionBlurbLow) {
        this.key = key;
        this.label = label;
        this.driverBlurbHigh = driverBlurbHigh;
        this.driverBlurbLow = driverBlurbLow;
        this.tensionBlurbHigh = tensionBlurbHigh;
        this.tensionBlurbLow = tensionBlurbLow;
    }

    public String key() { return key; }

    public String label() { return label; }

    /**
     * @param isDriver   true if this trait pushed the score up, false if it pushed it down
     * @param movieHigh  true if the MOVIE's own value on this axis is at/above neutral (0.5)
     */
    public String blurbFor(boolean isDriver, boolean movieHigh) {
        if (isDriver) {
            return movieHigh ? driverBlurbHigh : driverBlurbLow;
        }
        return movieHigh ? tensionBlurbHigh : tensionBlurbLow;
    }

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
