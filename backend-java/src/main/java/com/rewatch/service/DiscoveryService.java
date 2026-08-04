package com.rewatch.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.rewatch.dto.MovieDTO;
import com.rewatch.model.Rating;
import com.rewatch.model.Title;
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
     * High audience quality, low popularity — the titles a popularity-sorted
     * feed would bury. Scored for the user like everything else; this row
     * only changes which candidates are considered, not how they're scored.
     */
    public List<MovieDTO> hiddenGems(Long userId, int limit) {
        List<Title> all = titleRepo.findAll();
        if (all.isEmpty()) {
            return List.of();
        }

        double medianPopularity = medianOf(all.stream().map(Title::getPopularity).toList());
        Set<Long> rated = ratedTitleIds(userId);

        List<Title> candidates = all.stream()
                .filter(t -> !rated.contains(t.getId()))
                .filter(t -> t.getPopularity() <= medianPopularity)
                .filter(t -> t.normalisedQuality() >= 0.55)
                .sorted(Comparator.comparingDouble(Title::normalisedQuality).reversed())
                .limit(Math.max(limit * 3, 20))
                .toList();

        return scoreAndSort(candidates, userId, limit);
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
        Set<Long> rated = ratedTitleIds(userId);

        List<Title> candidates = titleRepo.findAll().stream()
                .filter(t -> !rated.contains(t.getId()))
                .sorted(Comparator.<Title>comparingDouble(t -> t.traitVector().centredCosine(userVec)).reversed())
                .limit(Math.max(limit * 2, 12))
                .toList();

        // scoreAndSort's own ordering IS the point here — it's already the
        // trait-vector similarity we just sorted candidates by, recomputed
        // through the same real scorer so the displayed % stays honest.
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
