package com.rewatch.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.rewatch.dto.MovieDTO;
import com.rewatch.model.Rating;
import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitVector;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;

/**
 * Discovery rows that are cheap once real trait vectors and a real
 * profile exist (Phases 2A/2B/3) — none of these need new data collection,
 * only different orderings of what's already there.
 */
@Service
public class DiscoveryService {

    private final TitleRepository titleRepo;
    private final RatingRepository ratingRepo;
    private final ProfileService profileService;
    private final Recommender recommender;

    public DiscoveryService(TitleRepository titleRepo, RatingRepository ratingRepo,
                            ProfileService profileService, Recommender recommender) {
        this.titleRepo = titleRepo;
        this.ratingRepo = ratingRepo;
        this.profileService = profileService;
        this.recommender = recommender;
    }

    /**
     * Below this, normalisedQuality() (a bare voteAverage/10, see Title) is
     * nearly meaningless — a 1928 Soviet film with 3 votes averaging 9/10
     * clears any quality bar this row could set, and "hidden gem" reads as
     * "we found a real audience nobody talks about," not "we found three
     * people." Deliberately below the main feed's MIN_VOTE_COUNT_FOR_RECOMMENDATION
     * (150) — this row's whole premise is lower popularity than the main feed,
     * just not zero-signal popularity.
     */
    private static final int HIDDEN_GEM_MIN_VOTE_COUNT = 40;

    /**
     * A cold-start user (few/no ratings, low profile confidence) has given the
     * system nothing to weigh a genuine deep cut against, so an untethered
     * "hidden gem" reads as random obscurity rather than a considered pick —
     * restrict to a recent window until there's enough taste signal to earn
     * the older, riskier picks. Matches the 15-rating threshold the "recording"
     * review itself proposed for when deep cuts become trustworthy.
     */
    private static final int HIDDEN_GEM_DEEP_CUT_RATING_THRESHOLD = 15;
    private static final int HIDDEN_GEM_RECENT_YEARS = 20;

    /**
     * High audience quality, low popularity — the titles a popularity-sorted
     * feed would bury. Scored for the user like everything else; this row
     * only changes which candidates are considered, not how they're scored.
     */
    public List<MovieDTO> hiddenGems(Long userId, int limit) {
        List<Title> all = titleRepo.findAll();
        if (all.isEmpty()) {
            return List.of();
        }

        Set<Long> rated = ratedTitleIds(userId);
        boolean deepCutsEarned = ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(userId).size()
                >= HIDDEN_GEM_DEEP_CUT_RATING_THRESHOLD;

        List<Title> candidates = hiddenGemCandidates(all, rated, deepCutsEarned, limit);
        return scoreAndSort(candidates, userId, limit);
    }

    /**
     * Package-private for direct testing — see DiscoveryServiceTest. Pure
     * filtering/ranking over an already-fetched title list, no repository or
     * scorer collaborators needed, the same split RecommenderTest already
     * uses for applyBrowseFilters().
     */
    List<Title> hiddenGemCandidates(List<Title> all, Set<Long> rated, boolean deepCutsEarned, int limit) {
        double medianPopularity = medianOf(all.stream().map(Title::getPopularity).toList());
        int minYear = java.time.Year.now().getValue() - HIDDEN_GEM_RECENT_YEARS;

        return all.stream()
                .filter(this::hasRenderableData)
                .filter(t -> !rated.contains(t.getId()))
                .filter(t -> t.getPopularity() <= medianPopularity)
                .filter(t -> t.normalisedQuality() >= 0.55)
                .filter(t -> t.getVoteCount() != null && t.getVoteCount() >= HIDDEN_GEM_MIN_VOTE_COUNT)
                .filter(t -> deepCutsEarned || t.getYear() >= minYear)
                .sorted(Comparator.comparingDouble(Title::normalisedQuality).reversed())
                .limit(Math.max(limit * 3, 20))
                .toList();
    }

    /**
     * A title with no synopsis or poster is broken data regardless of how well
     * it scores or how closely its vector matches — every Discovery row filters
     * candidates through this before ranking them.
     */
    private boolean hasRenderableData(Title t) {
        return t.getSynopsis() != null && !t.getSynopsis().isBlank()
                && t.getPoster() != null && !t.getPoster().isBlank();
    }

    /**
     * Titles whose own trait vector is closest to the user's single highest
     * rating — "more like the thing you loved," not "more like your whole
     * profile." Falls back to an empty row for users with no ratings yet
     * rather than guessing.
     */
    public List<MovieDTO> becauseYouLoved(Long userId, int limit) {
        List<Rating> ratings = ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(userId);
        if (ratings.isEmpty()) {
            return List.of();
        }

        Rating best = ratings.stream()
                .filter(r -> r.getOverall() != null)
                .max(Comparator.<Rating>comparingInt(r -> r.getOverall())
                        .thenComparing(Rating::getCreatedAt))
                .orElse(null);
        if (best == null) {
            return List.of();
        }

        Title source = titleRepo.findById(best.getTitleId()).orElse(null);
        if (source == null) {
            return List.of();
        }

        TraitVector sourceVec = source.traitVector();
        Set<Long> rated = ratedTitleIds(userId);

        List<Title> candidates = titleRepo.findAll().stream()
                .filter(this::hasRenderableData)
                .filter(t -> !t.getId().equals(source.getId()))
                .filter(t -> !rated.contains(t.getId()))
                .sorted(Comparator.<Title>comparingDouble(t -> t.traitVector().centredCosine(sourceVec)).reversed())
                .limit(Math.max(limit * 2, 12))
                .toList();

        List<MovieDTO> scored = scoreAndSort(candidates, userId, limit);
        // Preserve the similarity ordering (why this row exists) rather than
        // re-sorting by match score, which would just duplicate the main feed.
        scored.forEach(dto -> dto.getReasons().add(0, "Because you loved " + source.getTitle()));
        return scored;
    }

    private static final int FORGOTTEN_MIN_DAYS = 30;
    private static final int FORGOTTEN_MIN_RATING = 4;

    /** Package-private for direct testing — see DiscoveryServiceTest. */
    List<Rating> selectForgottenRatings(Long userId, int limit) {
        Instant cutoff = Instant.now().minus(FORGOTTEN_MIN_DAYS, ChronoUnit.DAYS);
        return ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .filter(r -> r.getOverall() != null && r.getOverall() >= FORGOTTEN_MIN_RATING)
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isBefore(cutoff))
                .sorted(Comparator.<Rating>comparingInt(Rating::getOverall).reversed()
                        .thenComparing(Rating::getCreatedAt))
                .limit(limit)
                .toList();
    }

    /**
     * Titles the user rated highly a while ago and hasn't touched since — a
     * rewatch prompt, not a new-discovery row. Unlike every other Discovery
     * row, this one deliberately does NOT exclude already-rated titles;
     * that's the whole point.
     */
    public List<MovieDTO> rediscoverForgotten(Long userId, int limit) {
        List<Rating> candidates = selectForgottenRatings(userId, limit);

        List<MovieDTO> out = new ArrayList<>();
        for (Rating r : candidates) {
            Title t = titleRepo.findById(r.getTitleId()).orElse(null);
            if (t == null) {
                continue;
            }
            MovieDTO dto = recommender.scoreForUser(t, userId);
            dto.getReasons().add(0, "You rated this " + r.getOverall() + "★ a while back — worth revisiting?");
            out.add(dto);
        }
        return out;
    }

    /**
     * Ranked by raw shape-similarity to the user's whole profile (centred
     * cosine across all ten axes), not by the salience-weighted match score.
     * The main feed already answers "what should you watch" by weighting the
     * axes you're opinionated about; this row answers a different question —
     * "what looks like you," full stop — so it can and will produce a
     * different order.
     */
    public List<MovieDTO> similarDna(Long userId, int limit) {
        var profile = profileService.currentProfile(userId);
        TraitVector userVec = profileService.vectorOf(profile);
        return rankByVectorSimilarity(userVec, userId, limit);
    }

    /** One curated collection's metadata (title/blurb shown in the UI) + target trait profile it's ranked against. */
    public record Collection(String slug, String title, String blurb, TraitVector target) {}

    /**
     * Scenario-based shelves ("Late Night Cry," "Post-Exam Comfort") ranked
     * the same way similarDna ranks against a user's own profile — the only
     * difference is the target vector is an editorial preset instead of a
     * live profile. This reuses the app's actual differentiator (trait-vector
     * similarity) rather than being a disconnected content list; a collection
     * is just "similarDna, but against a curated mood instead of you."
     */
    private static final List<Collection> COLLECTIONS = List.of(
            new Collection("late-night-cry", "Late Night Cry",
                    "Bittersweet, intense, the kind that earns its tears.",
                    vectorOf(Map.of(Trait.BITTER, 0.85, Trait.INTENSITY, 0.75, Trait.HOPE, 0.55, Trait.HUMOR, 0.2))),
            new Collection("post-exam-comfort", "Post-Exam Comfort",
                    "Low-stakes warmth for when you have nothing left to give.",
                    vectorOf(Map.of(Trait.COMFORT, 0.9, Trait.FAMILY, 0.7, Trait.INTENSITY, 0.15, Trait.HUMOR, 0.7))),
            new Collection("sunday-rain", "Sunday Rain",
                    "Slow, nostalgic, made for a grey afternoon indoors.",
                    vectorOf(Map.of(Trait.NOSTALGIA, 0.85, Trait.COMFORT, 0.75, Trait.PACING, 0.8, Trait.INTENSITY, 0.2))),
            new Collection("need-a-win-today", "Need a Win Today",
                    "Characters who claw their way to something better.",
                    vectorOf(Map.of(Trait.GROWTH, 0.85, Trait.HOPE, 0.85, Trait.INTENSITY, 0.5))),
            new Collection("healing-after-a-breakup", "Healing After a Breakup",
                    "Gentle on romance, generous on hope and found family.",
                    vectorOf(Map.of(Trait.COMFORT, 0.8, Trait.HOPE, 0.75, Trait.BITTER, 0.5, Trait.FAMILY, 0.6, Trait.ROMANCE, 0.3))),
            new Collection("found-family-feels", "Found Family Feels",
                    "Ensembles who choose each other.",
                    vectorOf(Map.of(Trait.FAMILY, 0.9, Trait.HOPE, 0.7, Trait.HUMOR, 0.5))),
            new Collection("slow-cinema", "Slow Cinema",
                    "Unhurried, contemplative, nothing to prove.",
                    vectorOf(Map.of(Trait.PACING, 0.9, Trait.INTENSITY, 0.25, Trait.NOSTALGIA, 0.6)))
    );

    private static TraitVector vectorOf(Map<Trait, Double> weights) {
        return TraitVector.fromMap(new EnumMap<>(weights));
    }

    /** Metadata only, for rendering the shelf-picker — no user context needed. */
    public List<Map<String, String>> listCollections() {
        List<Map<String, String>> out = new ArrayList<>(COLLECTIONS.size());
        for (Collection c : COLLECTIONS) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("slug", c.slug());
            m.put("title", c.title());
            m.put("blurb", c.blurb());
            out.add(m);
        }
        return out;
    }

    public List<MovieDTO> collection(String slug, Long userId, int limit) {
        Collection c = COLLECTIONS.stream().filter(x -> x.slug().equals(slug)).findFirst().orElse(null);
        if (c == null) {
            return List.of();
        }
        return rankByVectorSimilarity(c.target(), userId, limit);
    }

    private List<MovieDTO> rankByVectorSimilarity(TraitVector target, Long userId, int limit) {
        Set<Long> rated = ratedTitleIds(userId);

        List<Title> candidates = titleRepo.findAll().stream()
                .filter(this::hasRenderableData)
                .filter(t -> !rated.contains(t.getId()))
                .sorted(Comparator.<Title>comparingDouble(t -> t.traitVector().centredCosine(target)).reversed())
                .limit(Math.max(limit * 2, 12))
                .toList();

        // scoreForUserPreservingOrder's ordering IS the point here — it's
        // already the trait-vector similarity we just sorted candidates by,
        // recomputed through the same real scorer so the displayed % stays honest.
        return scoreForUserPreservingOrder(candidates, userId, limit);
    }

    private List<MovieDTO> scoreAndSort(List<Title> candidates, Long userId, int limit) {
        List<MovieDTO> scored = new ArrayList<>(candidates.size());
        for (Title t : candidates) {
            scored.add(recommender.scoreForUser(t, userId));
        }
        scored.sort(Comparator.comparingDouble(MovieDTO::getMatchScore).reversed());
        return new ArrayList<>(scored.subList(0, Math.min(limit, scored.size())));
    }

    private List<MovieDTO> scoreForUserPreservingOrder(List<Title> candidates, Long userId, int limit) {
        List<MovieDTO> scored = new ArrayList<>(candidates.size());
        for (Title t : candidates.subList(0, Math.min(limit, candidates.size()))) {
            scored.add(recommender.scoreForUser(t, userId));
        }
        return scored;
    }

    private Set<Long> ratedTitleIds(Long userId) {
        Set<Long> ids = new HashSet<>();
        ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(userId).forEach(r -> ids.add(r.getTitleId()));
        return ids;
    }

    private double medianOf(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(mid - 1) + sorted.get(mid)) / 2.0 : sorted.get(mid);
    }
}
