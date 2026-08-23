package com.rewatch.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.rewatch.features.GenreLexicon;
import com.rewatch.model.Rating;
import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitNode;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;

/**
 * A sharp, specific, data-backed read of a user's actual rating history —
 * the "Roast My Taste" share card. Deliberately NOT a generic horoscope: every
 * line here is grounded in a real number computed from that user's own
 * ratings/trait vector, the same discipline ArchetypeService already applies
 * to naming a taste profile, just aimed at being funny instead of earnest.
 * Built for the same reason MatchShareCard exists — a screenshot people
 * actually want to post — but where a match card celebrates a title, this is
 * meant to be a little mean, on purpose, in the way a friend teasing you
 * about your taste is mean.
 */
@Service
public class RoastService {

    /** Below this, there isn't enough real signal to roast honestly — see generate()'s null return. */
    public static final int MIN_RATINGS_FOR_ROAST = 5;

    private final RatingRepository ratingRepo;
    private final TitleRepository titleRepo;
    private final ProfileService profileService;

    public RoastService(RatingRepository ratingRepo, TitleRepository titleRepo, ProfileService profileService) {
        this.ratingRepo = ratingRepo;
        this.titleRepo = titleRepo;
        this.profileService = profileService;
    }

    public record Roast(String headline, List<String> receipts, int ratingCount) {}

    public Roast generate(Long userId) {
        List<Rating> ratings = ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(userId);
        if (ratings.size() < MIN_RATINGS_FOR_ROAST) {
            return null;
        }

        double avgOverall = ratings.stream()
                .filter(r -> r.getOverall() != null)
                .mapToInt(Rating::getOverall)
                .average()
                .orElse(0);
        long fiveStarCount = ratings.stream().filter(r -> Integer.valueOf(5).equals(r.getOverall())).count();
        long rewatchCount = ratings.stream().filter(r -> r.getRewatch() != null && r.getRewatch() > 0).count();

        GenreStat topGenre = topGenre(ratings);
        Map<Trait, TraitNode> profile = profileService.currentProfile(userId);

        List<String> receipts = new ArrayList<>();
        String headline = headlineFor(avgOverall, fiveStarCount, ratings.size(), topGenre);

        genreReceipt(topGenre, ratings.size()).ifPresent(receipts::add);
        ratingAverageReceipt(avgOverall, fiveStarCount, ratings.size()).ifPresent(receipts::add);
        rewatchReceipt(rewatchCount, ratings.size()).ifPresent(receipts::add);
        traitExtremeReceipt(profile).ifPresent(receipts::add);

        // A thin profile (met the ratings floor but nothing above landed a real
        // receipt) still deserves a punchline instead of an empty card.
        if (receipts.isEmpty()) {
            receipts.add("Nothing about your taste sticks out. You're the human equivalent of "
                    + "\"no strong feelings either way.\"");
        }

        return new Roast(headline, receipts.subList(0, Math.min(3, receipts.size())), ratings.size());
    }

    private record GenreStat(String name, long count, double share) {}

    private GenreStat topGenre(List<Rating> ratings) {
        Map<String, Long> counts = new java.util.HashMap<>();
        for (Rating r : ratings) {
            Title t = titleRepo.findById(r.getTitleId()).orElse(null);
            if (t == null) {
                continue;
            }
            for (String genre : GenreLexicon.namesFor(t.getGenreIds())) {
                counts.merge(genre, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> new GenreStat(e.getKey(), e.getValue(), (double) e.getValue() / ratings.size()))
                .orElse(null);
    }

    private String headlineFor(double avgOverall, long fiveStarCount, int total, GenreStat topGenre) {
        if (topGenre != null && topGenre.share() >= 0.5) {
            return "You don't have a favorite genre. You have a genre problem.";
        }
        if (avgOverall >= 4.5) {
            return "Nothing has ever disappointed you, and that's suspicious.";
        }
        if (avgOverall <= 2.5) {
            return "You've never met a movie that met your standards.";
        }
        if (total >= 100) {
            return "You've rated " + total + " titles. At this point it's not a hobby, it's a lifestyle.";
        }
        return "Your taste, on the record, for once.";
    }

    private Optional<String> genreReceipt(GenreStat topGenre, int total) {
        if (topGenre == null) {
            return Optional.empty();
        }
        int pct = (int) Math.round(topGenre.share() * 100);
        if (pct >= 60) {
            return Optional.of(pct + "% of what you rate is " + topGenre.name()
                    + ". At this point it's not a preference, it's a personality.");
        }
        if (pct >= 35) {
            return Optional.of(topGenre.name() + " shows up in " + pct
                    + "% of your ratings. We get it, you have a type.");
        }
        return Optional.empty();
    }

    private Optional<String> ratingAverageReceipt(double avgOverall, long fiveStarCount, int total) {
        int fiveStarPct = (int) Math.round(100.0 * fiveStarCount / total);
        if (fiveStarPct >= 50) {
            return Optional.of(fiveStarPct + "% of your ratings are 5 stars. Either you have "
                    + "impeccable taste or \"standards\" is more of a suggestion to you.");
        }
        if (avgOverall <= 2.7) {
            return Optional.of(String.format(java.util.Locale.ROOT,
                    "Average rating: %.1f/5. Hollywood is trying its best out here.", avgOverall));
        }
        return Optional.empty();
    }

    private Optional<String> rewatchReceipt(long rewatchCount, int total) {
        if (rewatchCount >= Math.max(5, total / 4)) {
            return Optional.of("You've rewatched something " + rewatchCount
                    + " times instead of trying anything new. Comfort zone: located, and never leaving.");
        }
        if (rewatchCount == 0 && total >= 20) {
            return Optional.of("Not a single rewatch in " + total
                    + " ratings. Commitment issues, or just a completionist? We don't judge. (We do.)");
        }
        return Optional.empty();
    }

    private Optional<String> traitExtremeReceipt(Map<Trait, TraitNode> profile) {
        Trait extremeTrait = null;
        double extremeDistance = 0;
        boolean extremeHigh = true;
        for (Trait t : Trait.values()) {
            TraitNode node = profile.get(t);
            if (node == null || node.getConf() < 0.5) {
                continue;
            }
            double distance = Math.abs(node.getVal() - 0.5);
            if (distance > extremeDistance) {
                extremeDistance = distance;
                extremeTrait = t;
                extremeHigh = node.getVal() >= 0.5;
            }
        }
        if (extremeTrait == null || extremeDistance < 0.25) {
            return Optional.empty();
        }
        String label = extremeTrait.label();
        return Optional.of(extremeHigh
                ? "Your " + label + " score is off the charts. It's not subtle."
                : "Your " + label + " score is basically zero. Noted, and a little concerning.");
    }
}
