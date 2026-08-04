package com.rewatch.features;

import java.util.HashMap;
import java.util.Map;

/**
 * TMDB genre id -> trait nudge.
 *
 * This layer costs ZERO extra API calls: `genre_ids` already arrives in every
 * /discover and /movie/popular list response, and the old parser threw it away.
 * That makes it possible to ship genuinely content-derived vectors for the whole
 * catalog before a single keyword request is made.
 *
 * Every entry carries negative components as well as positive ones. That is what
 * produces spread across the catalog — a lexicon that only ever adds pushes every
 * title into the same high corner of trait space.
 */
public final class GenreLexicon {

    private GenreLexicon() {}

    private static final Map<Integer, TraitDelta> MOVIE = new HashMap<>();

    static {
        // --- TMDB movie genres ---
        put(28, TraitDelta.of()          // Action
                .intensity(0.55).pacing(-0.50).growth(-0.15).comfort(-0.20).bitter(-0.10));

        put(12, TraitDelta.of()          // Adventure
                .hope(0.35).family(0.25).pacing(-0.30).intensity(0.15).comfort(0.10));

        put(16, TraitDelta.of()          // Animation
                .nostalgia(0.40).comfort(0.35).family(0.25).humor(0.20).intensity(-0.15));

        put(35, TraitDelta.of()          // Comedy
                .humor(0.70).comfort(0.35).intensity(-0.35).bitter(-0.25).hope(0.20));

        put(80, TraitDelta.of()          // Crime
                .intensity(0.45).bitter(0.30).comfort(-0.45).hope(-0.25).humor(-0.20));

        put(99, TraitDelta.of()          // Documentary
                .pacing(0.30).growth(0.15).romance(-0.30).humor(-0.15));

        put(18, TraitDelta.of()          // Drama
                .growth(0.45).bitter(0.35).pacing(0.35).intensity(0.20).humor(-0.30));

        put(10751, TraitDelta.of()       // Family
                .family(0.65).comfort(0.50).humor(0.30).hope(0.35).intensity(-0.40).bitter(-0.30));

        put(14, TraitDelta.of()          // Fantasy
                .nostalgia(0.35).hope(0.25).family(0.20).comfort(0.15));

        put(36, TraitDelta.of()          // History
                .nostalgia(0.55).pacing(0.35).bitter(0.20).humor(-0.20));

        put(27, TraitDelta.of()          // Horror
                .intensity(0.75).comfort(-0.70).hope(-0.40).humor(-0.25).family(-0.20));

        put(10402, TraitDelta.of()       // Music
                .nostalgia(0.35).hope(0.25).comfort(0.25).romance(0.15));

        put(9648, TraitDelta.of()        // Mystery
                .pacing(0.45).intensity(0.30).comfort(-0.25).humor(-0.20).bitter(0.15));

        put(10749, TraitDelta.of()       // Romance
                .romance(0.80).comfort(0.25).bitter(0.20).hope(0.20).intensity(0.15));

        put(878, TraitDelta.of()         // Science Fiction
                .intensity(0.25).growth(0.15).comfort(-0.20).nostalgia(-0.10));

        put(10770, TraitDelta.of()       // TV Movie
                .comfort(0.20).intensity(-0.15));

        put(53, TraitDelta.of()          // Thriller
                .intensity(0.60).pacing(-0.25).comfort(-0.50).hope(-0.20).humor(-0.25));

        put(10752, TraitDelta.of()       // War
                .bitter(0.55).intensity(0.50).comfort(-0.45).hope(-0.25).humor(-0.30));

        put(37, TraitDelta.of()          // Western
                .nostalgia(0.35).pacing(0.25).intensity(0.20).comfort(-0.15));

        // --- TMDB TV genres (distinct id space for a few) ---
        put(10759, TraitDelta.of()       // Action & Adventure
                .intensity(0.45).pacing(-0.35).hope(0.20));

        put(10762, TraitDelta.of()       // Kids
                .family(0.55).comfort(0.60).humor(0.35).intensity(-0.50).bitter(-0.35));

        put(10763, TraitDelta.of()       // News
                .pacing(0.20).romance(-0.30).humor(-0.25));

        put(10764, TraitDelta.of()       // Reality
                .humor(0.25).comfort(0.20).growth(-0.20));

        put(10765, TraitDelta.of()       // Sci-Fi & Fantasy
                .intensity(0.25).nostalgia(0.20).hope(0.15));

        put(10766, TraitDelta.of()       // Soap
                .romance(0.55).bitter(0.30).intensity(0.25).pacing(0.20));

        put(10767, TraitDelta.of()       // Talk
                .humor(0.30).comfort(0.20).growth(-0.20));

        put(10768, TraitDelta.of()       // War & Politics
                .bitter(0.45).intensity(0.40).comfort(-0.40).humor(-0.25));
    }

    private static void put(int id, TraitDelta.Builder b) {
        MOVIE.put(id, b.build());
    }

    public static TraitDelta forGenre(int genreId) {
        return MOVIE.get(genreId);
    }

    /**
     * Signals available for free on every list response that aren't genres.
     * These encode what the old code was groping at with a hardcoded
     * `with_original_language=ja&with_genres=16` discover filter — as an actual
     * rule rather than a fixed query.
     */
    public static TraitDelta forLanguage(String lang, boolean isAnimation) {
        if (lang == null) {
            return null;
        }
        switch (lang) {
            case "ja":
                return isAnimation
                        ? TraitDelta.of().nostalgia(0.30).comfort(0.25).family(0.25).growth(0.20).build()
                        : TraitDelta.of().pacing(0.25).bitter(0.20).build();
            case "ko":
                return TraitDelta.of().bitter(0.25).romance(0.20).intensity(0.15).pacing(0.15).build();
            default:
                return null;
        }
    }
}
