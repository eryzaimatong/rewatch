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

    private final TitleRepository titleRepo;

    public OnboardingService(TitleRepository titleRepo) {
        this.titleRepo = titleRepo;
    }

    public TraitVector deriveSeed(OnboardingRequest req) {
        double[] raw = new double[Trait.count()];

        // Favourites: average the trait vectors of any we can match in the catalog.
        List<TraitVector> favVectors = new ArrayList<>();
        if (req.getFavs() != null) {
            List<Title> all = titleRepo.findAll();
            for (String fav : req.getFavs()) {
                findByTitle(all, fav).ifPresent(t -> favVectors.add(t.traitVector()));
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

        double[] squashed = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            squashed[i] = 1.0 / (1.0 + Math.exp(-0.9 * raw[i]));
        }
        TraitVector seed = TraitVector.of(squashed);

        // Pacing slider: 1 (very slow) .. 5 (hyper fast). Our PACING trait means
        // "prefers slow-burn", so this is inverted.
        if (req.getPacing() != null) {
            double t = clampSlider(req.getPacing(), 1, 5);
            seed = seed.with(Trait.PACING, 1.0 - t);
        }

        // Intensity slider: 1 (cozy) .. 10 (soul crushing) maps directly.
        if (req.getIntensity() != null) {
            double t = clampSlider(req.getIntensity(), 1, 10);
            seed = seed.with(Trait.INTENSITY, t);
        }

        return seed;
    }

    private double clampSlider(int value, int min, int max) {
        double t = (value - min) / (double) (max - min);
        return Math.max(0.0, Math.min(1.0, t));
    }

    private java.util.Optional<Title> findByTitle(List<Title> all, String name) {
        if (name == null) {
            return java.util.Optional.empty();
        }
        String needle = name.trim().toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(t -> t.getTitle() != null && t.getTitle().trim().toLowerCase(Locale.ROOT).equals(needle))
                .findFirst();
    }
}
