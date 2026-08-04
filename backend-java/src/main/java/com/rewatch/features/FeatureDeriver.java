package com.rewatch.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.rewatch.model.Trait;
import com.rewatch.model.TraitVector;

/**
 * Turns raw TMDB signal (genres, keywords, overview text) into a {@link TraitVector}.
 *
 * This replaces the previous approach, in which a movie's "story vector" was
 * computed from its index in the response array:
 *
 *     new StoryVector(75 + (i*3%20), 80 + (i*2%15), ...)
 *
 * — so two different films at the same index got identical vectors, and the same
 * film got a different vector depending on where it happened to land in the list.
 * Every match percentage in the app was therefore fictional.
 */
@Component
public class FeatureDeriver {

    /** Steepness of the squash. Chosen so ~3 strong deltas approach saturation. */
    private static final double LOGISTIC_K = 0.9;

    /** Overview text is weaker evidence than a curated keyword. */
    private static final double OVERVIEW_WEIGHT = 0.4;

    private static final int MIN_KEYWORD_HITS = 3;

    public record Derived(
            TraitVector vector,
            double confidence,
            FeaturesSource source,
            int keywordHits) {}

    public Derived derive(List<Integer> genreIds,
                          List<String> keywords,
                          String overview,
                          String originalLanguage) {

        double[] raw = new double[Trait.count()];
        int genreCount = 0;
        boolean isAnimation = genreIds != null && genreIds.contains(16);

        // --- Layer 1: genres (free — already in every list response) ---
        if (genreIds != null) {
            for (Integer g : genreIds) {
                if (g == null) {
                    continue;
                }
                TraitDelta d = GenreLexicon.forGenre(g);
                if (d != null) {
                    add(raw, d, 1.0);
                    genreCount++;
                }
            }
        }

        TraitDelta langDelta = GenreLexicon.forLanguage(originalLanguage, isAnimation);
        if (langDelta != null) {
            add(raw, langDelta, 1.0);
        }

        // --- Layer 2: keywords (one API call per title) ---
        int hits = 0;
        if (keywords != null) {
            for (String kw : keywords) {
                TraitDelta d = KeywordLexicon.lookup(kw);
                if (d != null) {
                    add(raw, d, 1.0);
                    hits++;
                }
            }
        }

        // --- Tier 2 fallback: scan the overview we already have ---
        boolean usedOverview = false;
        if (hits < MIN_KEYWORD_HITS && overview != null && !overview.isBlank()) {
            List<TraitDelta> textHits = KeywordLexicon.scanText(overview);
            for (TraitDelta d : textHits) {
                add(raw, d, OVERVIEW_WEIGHT);
            }
            usedOverview = !textHits.isEmpty();
        }

        FeaturesSource source = classify(hits, usedOverview, genreCount);

        // --- Squash to 0..1 with a FIXED logistic ---
        // Deliberately not normalise-by-max or z-score against the catalog: a title's
        // stated traits must never change because unrelated titles were ingested,
        // otherwise a printed explanation stops being reproducible. raw=0 maps to
        // exactly 0.5, which correctly reads as "no signal" rather than "medium".
        double[] squashed = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            squashed[i] = 1.0 / (1.0 + Math.exp(-LOGISTIC_K * raw[i]));
        }

        // --- Shrink toward neutral by how much we actually know ---
        double confidence = confidenceFor(hits, genreCount, usedOverview, source);
        TraitVector vec = TraitVector.of(squashed).shrinkToNeutral(confidence);

        return new Derived(vec, confidence, source, hits);
    }

    private FeaturesSource classify(int hits, boolean usedOverview, int genreCount) {
        if (hits >= MIN_KEYWORD_HITS) {
            return FeaturesSource.TMDB_KEYWORDS;
        }
        if (usedOverview) {
            return FeaturesSource.OVERVIEW;
        }
        if (genreCount > 0) {
            return FeaturesSource.GENRE_ONLY;
        }
        return FeaturesSource.DEFAULT;
    }

    private double confidenceFor(int hits, int genreCount, boolean usedOverview, FeaturesSource source) {
        if (source == FeaturesSource.DEFAULT) {
            return 0.10;
        }
        double c = 0.25 + (0.05 * hits) + (0.06 * genreCount);
        if (usedOverview) {
            c += 0.05;
        }
        return TraitVector.clamp(Math.min(c, 0.95));
    }

    private void add(double[] acc, TraitDelta d, double scale) {
        double[] delta = d.raw();
        for (int i = 0; i < acc.length; i++) {
            acc[i] += delta[i] * scale;
        }
    }

    /** Comma-separated genre id string ("28,12,878") -> list. Tolerates junk. */
    public static List<Integer> parseGenreIds(String csv) {
        List<Integer> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String part : csv.split(",")) {
            try {
                out.add(Integer.valueOf(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip malformed entries rather than failing the whole derivation
            }
        }
        return out;
    }

    public static List<String> parseKeywords(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
