package com.rewatch.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.rewatch.dto.OnboardingRequest;
import com.rewatch.features.TraitDelta;
import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitVector;
import com.rewatch.repository.TitleRepository;

/**
 * Turns the onboarding wizard's answers into a starting {@link TraitVector}.
 *
 * Replaces the old /api/movies/onboard, which computed a handful of hardcoded
 * constants (comfort=0.80, family=0.85, ...) that barely reacted to what the user
 * actually picked and were never saved anywhere. This one is real: it looks up
 * the user's chosen favourites in the catalog, averages their trait vectors,
 * layers on genre sentiment and the pacing/intensity sliders, and hands the
 * result to ProfileService to seed and replay.
 */
@Service
public class OnboardingService {

    // UI genre label -> trait nudge. Distinct from GenreLexicon (which keys off
    // TMDB genre ids) because Onboarding.jsx uses its own coarser category names.
    private static final Map<String, TraitDelta> GENRE_SENTIMENT = new HashMap<>();
    static {
        GENRE_SENTIMENT.put("drama", TraitDelta.of().growth(0.4).bitter(0.35).pacing(0.3).build());
        GENRE_SENTIMENT.put("sci-fi", TraitDelta.of().intensity(0.25).growth(0.2).comfort(-0.15).build());
        GENRE_SENTIMENT.put("slice of life", TraitDelta.of().comfort(0.6).pacing(0.5).family(0.25).build());
        GENRE_SENTIMENT.put("romance", TraitDelta.of().romance(0.7).hope(0.2).build());
        GENRE_SENTIMENT.put("thriller", TraitDelta.of().intensity(0.55).comfort(-0.4).pacing(-0.2).build());
        GENRE_SENTIMENT.put("anime", TraitDelta.of().nostalgia(0.3).comfort(0.2).family(0.2).build());
        GENRE_SENTIMENT.put("comedy", TraitDelta.of().humor(0.65).comfort(0.3).intensity(-0.3).build());
        GENRE_SENTIMENT.put("mystery", TraitDelta.of().pacing(0.4).intensity(0.3).comfort(-0.2).build());
    }

    // Story-trope picks -> trait nudge. Same style as GENRE_SENTIMENT; every
    // trope the onboarding wizard offers maps to real trait signal, matching
    // the rule enforced everywhere else in this codebase (see
    // ProfileService.weightFor's "RELEVANCE IS THE LOAD-BEARING TERM").
    private static final Map<String, TraitDelta> TROPE_SENTIMENT = new HashMap<>();
    static {
        TROPE_SENTIMENT.put("character growth", TraitDelta.of().growth(0.6).build());
        TROPE_SENTIMENT.put("slow burn", TraitDelta.of().pacing(0.5).build());
        TROPE_SENTIMENT.put("found family", TraitDelta.of().family(0.6).comfort(0.3).build());
        TROPE_SENTIMENT.put("enemies to lovers", TraitDelta.of().romance(0.6).intensity(0.3).build());
        TROPE_SENTIMENT.put("psychological", TraitDelta.of().intensity(0.55).growth(0.25).build());
        TROPE_SENTIMENT.put("bittersweet endings", TraitDelta.of().bitter(0.5).hope(0.2).build());
        TROPE_SENTIMENT.put("hopeful payoffs", TraitDelta.of().hope(0.6).build());
        TROPE_SENTIMENT.put("comfort watch", TraitDelta.of().comfort(0.65).intensity(-0.2).build());
    }

    // "I want stories that make me..." picks -> trait nudge.
    private static final Map<String, TraitDelta> EMOTIONAL_GOAL_SENTIMENT = new HashMap<>();
    static {
        EMOTIONAL_GOAL_SENTIMENT.put("laugh", TraitDelta.of().humor(0.7).build());
        EMOTIONAL_GOAL_SENTIMENT.put("cry", TraitDelta.of().bitter(0.5).hope(0.3).build());
        EMOTIONAL_GOAL_SENTIMENT.put("think", TraitDelta.of().growth(0.5).intensity(0.3).build());
        EMOTIONAL_GOAL_SENTIMENT.put("escape", TraitDelta.of().comfort(0.5).intensity(-0.4).build());
        EMOTIONAL_GOAL_SENTIMENT.put("relax", TraitDelta.of().comfort(0.6).pacing(0.3).build());
        EMOTIONAL_GOAL_SENTIMENT.put("feel hope", TraitDelta.of().hope(0.7).build());
    }

    // Dealbreakers ("things you avoid"). Unlike GENRE_SENTIMENT, there's no
    // separate love/avoid sign to apply here — each delta already points away
    // from what's being avoided (the same way GENRE_SENTIMENT's "thriller"
    // entry already carries a negative comfort component within an otherwise
    // positive one), so a selection just adds its delta directly.
    private static final Map<String, TraitDelta> DEALBREAKER_SENTIMENT = new HashMap<>();
    static {
        DEALBREAKER_SENTIMENT.put("excessive gore", TraitDelta.of().intensity(-0.6).build());
        DEALBREAKER_SENTIMENT.put("sad ending", TraitDelta.of().hope(0.5).bitter(-0.4).build());
        DEALBREAKER_SENTIMENT.put("open ending", TraitDelta.of().bitter(-0.4).build());
        DEALBREAKER_SENTIMENT.put("love triangle", TraitDelta.of().romance(-0.35).intensity(-0.15).build());
        DEALBREAKER_SENTIMENT.put("jump scares", TraitDelta.of().intensity(-0.5).build());
        DEALBREAKER_SENTIMENT.put("slow start", TraitDelta.of().pacing(-0.5).build());
        DEALBREAKER_SENTIMENT.put("animal death", TraitDelta.of().bitter(-0.4).intensity(-0.2).build());
        DEALBREAKER_SENTIMENT.put("cheating", TraitDelta.of().romance(-0.3).bitter(-0.2).build());
        DEALBREAKER_SENTIMENT.put("unresolved ending", TraitDelta.of().bitter(-0.4).build());
        // "Poor CGI" intentionally has no entry — production quality isn't a
        // story-shape trait, mapping it would be decorative, not signal.
    }

    private final TitleRepository titleRepo;

    public OnboardingService(TitleRepository titleRepo) {
        this.titleRepo = titleRepo;
    }

    public TraitVector deriveSeed(OnboardingRequest req) {
        double[] raw = new double[Trait.count()];

        // Favourites: average the trait vectors of any we can match in the catalog.
        // Was titleRepo.findAll() + a linear scan per favourite — loaded and
        // Hibernate-managed all ~6,000 titles (measured live: a 6,026-entity
        // flush, 1.9s of DB time) on every submission, INCLUDING a "Skip for
        // now" with zero favourites — !favs.isEmpty() below fixes that half;
        // the targeted lookup fixes the other half.
        List<TraitVector> favVectors = new ArrayList<>();
        if (req.getFavs() != null && !req.getFavs().isEmpty()) {
            List<String> needles = req.getFavs().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(f -> f.trim().toLowerCase(Locale.ROOT))
                    .toList();
            Map<String, Title> byNeedle = new HashMap<>();
            for (Title t : titleRepo.findByTitleTrimmedLowerIn(needles)) {
                byNeedle.putIfAbsent(t.getTitle().trim().toLowerCase(Locale.ROOT), t);
            }
            for (String needle : needles) {
                Title t = byNeedle.get(needle);
                if (t != null) {
                    favVectors.add(t.traitVector());
                }
            }
        }
        if (!favVectors.isEmpty()) {
            TraitVector favMean = TraitVector.mean(favVectors);
            for (Trait t : Trait.values()) {
                // centred on neutral so this behaves like the other additive layers
                raw[t.ordinal()] += 2.0 * (favMean.get(t) - TraitVector.NEUTRAL);
            }
        }

        // Genre sentiment: "love" adds the delta, "avoid" subtracts it.
        if (req.getGenres() != null) {
            for (Map.Entry<String, String> e : req.getGenres().entrySet()) {
                TraitDelta d = GENRE_SENTIMENT.get(e.getKey().toLowerCase(Locale.ROOT));
                if (d == null) {
                    continue;
                }
                double sign = switch (e.getValue() == null ? "" : e.getValue().toLowerCase(Locale.ROOT)) {
                    case "love" -> 1.0;
                    case "avoid" -> -1.0;
                    default -> 0.0;
                };
                if (sign == 0.0) {
                    continue;
                }
                double[] delta = d.raw();
                for (int i = 0; i < raw.length; i++) {
                    raw[i] += delta[i] * sign;
                }
            }
        }

        addSelections(raw, req.getTropes(), TROPE_SENTIMENT);
        addSelections(raw, req.getEmotionalGoals(), EMOTIONAL_GOAL_SENTIMENT);
        addSelections(raw, req.getAvoid(), DEALBREAKER_SENTIMENT);

        double[] squashed = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            squashed[i] = 1.0 / (1.0 + Math.exp(-0.9 * raw[i]));
        }
        TraitVector seed = TraitVector.of(squashed);

        // Pacing slider: 1 (very slow) .. 5 (hyper fast). Our PACING trait means
        // "prefers slow-burn", so this is inverted.
        if (req.getPacing() != null) {
            double t = clampSlider(req.getPacing(), 1, 5);
            seed = seed.with(Trait.PACING, shrinkSlider(1.0 - t));
        }

        // Intensity slider: 1 (cozy) .. 10 (soul crushing) maps directly.
        if (req.getIntensity() != null) {
            double t = clampSlider(req.getIntensity(), 1, 10);
            seed = seed.with(Trait.INTENSITY, shrinkSlider(t));
        }

        return seed;
    }

    // A max slider pick maps to raw t=1.0 (or t=0.0), which would set a trait
    // to exactly 0 or 1 — more extreme than any evidence-based pathway in this
    // codebase can ever produce (movie vectors are shrunk toward neutral by a
    // confidence factor capped at 0.95, see FeatureDeriver). Sliders are
    // trustworthy explicit input, more so than a keyword guess, but still
    // shouldn't be able to seed a more extreme, falsely-confident value than
    // real accumulated evidence ever could.
    private static final double SLIDER_SHRINK = 0.9;

    private double shrinkSlider(double t) {
        return 0.5 + (t - 0.5) * SLIDER_SHRINK;
    }

    private double clampSlider(int value, int min, int max) {
        double t = (value - min) / (double) (max - min);
        return Math.max(0.0, Math.min(1.0, t));
    }

    private void addSelections(double[] raw, List<String> selections, Map<String, TraitDelta> sentiment) {
        if (selections == null) {
            return;
        }
        for (String s : selections) {
            if (s == null) {
                continue;
            }
            TraitDelta d = sentiment.get(s.trim().toLowerCase(Locale.ROOT));
            if (d == null) {
                continue;
            }
            double[] delta = d.raw();
            for (int i = 0; i < raw.length; i++) {
                raw[i] += delta[i];
            }
        }
    }
}
