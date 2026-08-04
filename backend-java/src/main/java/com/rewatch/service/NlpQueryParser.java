package com.rewatch.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.rewatch.features.KeywordLexicon;
import com.rewatch.features.TraitDelta;
import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitVector;

/**
 * Turns a free-text mood query into a target trait vector.
 *
 * This is a local lexicon-based parser, not an LLM call — no API key, no cost,
 * works offline, and is labelled "semantic search" in the UI rather than "AI" so
 * it doesn't overclaim. It reuses {@link KeywordLexicon}, the same lexicon that
 * derives movie vectors, so a search for "cozy" and a movie tagged "cozy" land in
 * the same trait space.
 *
 * Two things this does beyond a plain keyword scan:
 *   - COMPARATIVE queries ("like Your Name but happier") anchor on a matched
 *     title's own vector and nudge it, rather than starting from neutral.
 *   - NEGATION ("not too sad", "less violent") flips the sign of whatever it
 *     modifies instead of ignoring it.
 *
 * Full-catalog phrase coverage and autocomplete are a Phase 7 concern; this
 * covers the comparative + negation cases the product review specifically asked
 * for ("something like Your Name but with a happier ending").
 */
@Service
public class NlpQueryParser {

    private static final Pattern LIKE_PATTERN =
            Pattern.compile("\\blike\\s+([a-z0-9' ]{3,40}?)(?:\\s+but\\b|\\s+except\\b|$)", Pattern.CASE_INSENSITIVE);

    private static final Pattern NEGATION_BEFORE =
            Pattern.compile("\\b(not|n't|less|without|no)\\s+(\\w+\\s+){0,2}$", Pattern.CASE_INSENSITIVE);

    /** Comparative adjectives that nudge specific axes directly. */
    private record Modifier(String word, TraitDelta delta) {}

    private static final List<Modifier> MODIFIERS = List.of(
            new Modifier("happier", TraitDelta.of().hope(0.5).bitter(-0.3).build()),
            new Modifier("happy", TraitDelta.of().hope(0.4).bitter(-0.2).build()),
            new Modifier("sadder", TraitDelta.of().bitter(0.5).hope(-0.3).build()),
            new Modifier("darker", TraitDelta.of().bitter(0.4).intensity(0.3).comfort(-0.3).build()),
            new Modifier("lighter", TraitDelta.of().humor(0.4).comfort(0.3).intensity(-0.3).build()),
            new Modifier("faster", TraitDelta.of().pacing(-0.4).build()),
            new Modifier("slower", TraitDelta.of().pacing(0.4).build()),
            new Modifier("warmer", TraitDelta.of().comfort(0.4).family(0.2).build()),
            new Modifier("colder", TraitDelta.of().comfort(-0.4).build()),
            new Modifier("scarier", TraitDelta.of().intensity(0.5).comfort(-0.4).build()),
            new Modifier("funnier", TraitDelta.of().humor(0.5).build()),
            new Modifier("romantic", TraitDelta.of().romance(0.5).build()),
            new Modifier("hopeful", TraitDelta.of().hope(0.5).build())
    );

    public record ParsedQuery(TraitVector targetVector, double[] confidence, Title anchor) {}

    public ParsedQuery parse(String query, List<Title> catalog) {
        String q = query.trim().toLowerCase(Locale.ROOT);

        Title anchor = findAnchor(q, catalog);
        double[] raw = new double[Trait.count()];
        double[] hitStrength = new double[Trait.count()];

        if (anchor != null) {
            TraitVector av = anchor.traitVector();
            for (Trait t : Trait.values()) {
                raw[t.ordinal()] += 2.0 * (av.get(t) - TraitVector.NEUTRAL);
            }
        }

        // Word-level scan so negation can look at what immediately precedes a hit.
        String[] words = q.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String prefix = String.join(" ", java.util.Arrays.copyOfRange(words, 0, i));
            boolean negated = NEGATION_BEFORE.matcher(prefix).find();
            double sign = negated ? -1.0 : 1.0;

            for (Modifier m : MODIFIERS) {
                if (words[i].startsWith(m.word().substring(0, Math.min(4, m.word().length())))
                        && words[i].contains(m.word().substring(0, Math.min(4, m.word().length())))) {
                    apply(raw, hitStrength, m.delta(), sign);
                }
            }

            TraitDelta d = KeywordLexicon.lookup(words[i]);
            if (d != null) {
                apply(raw, hitStrength, d, sign);
            }
        }

        // Also run the pattern scanner over the whole query for multi-word phrases.
        for (TraitDelta d : KeywordLexicon.scanText(q)) {
            apply(raw, hitStrength, d, 1.0);
        }

        double[] squashed = new double[raw.length];
        double[] confidence = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            squashed[i] = 1.0 / (1.0 + Math.exp(-0.9 * raw[i]));
            confidence[i] = anchor != null
                    ? Math.max(0.4, Math.min(0.95, 0.4 + hitStrength[i]))
                    : Math.max(0.15, Math.min(0.95, hitStrength[i]));
        }

        return new ParsedQuery(TraitVector.of(squashed), confidence, anchor);
    }

    private void apply(double[] raw, double[] hitStrength, TraitDelta d, double sign) {
        double[] delta = d.raw();
        for (int i = 0; i < raw.length; i++) {
            if (delta[i] != 0.0) {
                raw[i] += delta[i] * sign;
                hitStrength[i] += Math.abs(delta[i]);
            }
        }
    }

    private Title findAnchor(String query, List<Title> catalog) {
        Matcher m = LIKE_PATTERN.matcher(query);
        if (!m.find()) {
            return null;
        }
        String needle = m.group(1).trim();
        Title best = null;
        int bestLen = -1;
        for (Title t : catalog) {
            if (t.getTitle() == null) {
                continue;
            }
            String candidate = t.getTitle().toLowerCase(Locale.ROOT);
            if (needle.contains(candidate) && candidate.length() > bestLen) {
                best = t;
                bestLen = candidate.length();
            }
        }
        return best;
    }
}
