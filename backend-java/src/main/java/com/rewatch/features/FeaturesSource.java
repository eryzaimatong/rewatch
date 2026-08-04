package com.rewatch.features;

/**
 * How much real signal a title's trait vector was derived from.
 *
 * Recorded per title and surfaced in the API so the UI can be honest about
 * uncertainty rather than presenting every number with equal authority.
 */
public enum FeaturesSource {

    /** Three or more curated keyword hits — the good case. */
    TMDB_KEYWORDS,

    /** Too few keywords, so pattern rules were run over the overview text. */
    OVERVIEW,

    /** Genres only. Free, no extra API call, but coarse. */
    GENRE_ONLY,

    /** Nothing usable. Vector sits at neutral and the title ranks mid-pack. */
    DEFAULT
}
