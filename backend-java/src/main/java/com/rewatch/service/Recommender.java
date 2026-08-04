package com.rewatch.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.rewatch.dto.MovieDTO;
import com.rewatch.dto.TraitContribution;
import com.rewatch.model.Rating;
import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitNode;
import com.rewatch.model.TraitVector;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;

/**
 * Ranks the local catalog for a user and attaches the explanation for each result.
 *
 * Replaces two mutually inconsistent scorers that used to coexist: an L1 distance
 * over five axes against the database, and an L2 distance over six axes against
 * synthetic vectors — so the same title could report two different match
 * percentages depending on which endpoint served it.
 */
@Service
public class Recommender {

    /** Diversity strength for the MMR re-rank. 0 = pure score order. */
    private static final double DIVERSITY_LAMBDA = 0.3;

    private final TitleRepository titleRepo;
    private final RatingRepository ratingRepo;
    private final ProfileService profileService;
    private final ScoringService scoringService;

    public Recommender(TitleRepository titleRepo,
                       RatingRepository ratingRepo,
                       ProfileService profileService,
                       ScoringService scoringService) {
        this.titleRepo = titleRepo;
        this.ratingRepo = ratingRepo;
        this.profileService = profileService;
        this.scoringService = scoringService;
    }

    public List<MovieDTO> recommend(Long userId, int limit, boolean excludeRated, boolean diversify) {
        List<Title> candidates = titleRepo.findAll();
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Trait, TraitNode> profile = profileService.currentProfile(userId);
        TraitVector user = profileService.vectorOf(profile);
        double[] confidence = profileService.confidencesOf(profile);
        double meanConf = profileService.meanConfidence(profile);

        Set<Long> ratedTitleIds = new HashSet<>();
        for (Rating r : ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(userId)) {
            ratedTitleIds.add(r.getTitleId());
        }

        List<MovieDTO> scored = new ArrayList<>(candidates.size());
        for (Title t : candidates) {
            boolean isRated = ratedTitleIds.contains(t.getId());
            if (excludeRated && isRated) {
                continue;
            }
            MovieDTO dto = score(t, user, confidence, meanConf);
            dto.setRated(isRated);
            scored.add(dto);
        }

        scored.sort(Comparator.comparingDouble(MovieDTO::getMatchScore).reversed());

        if (diversify && scored.size() > limit) {
            return mmrRerank(scored, limit);
        }
        return scored.subList(0, Math.min(limit, scored.size()));
    }

    public MovieDTO scoreForUser(Title title, Long userId) {
        Map<Trait, TraitNode> profile = profileService.currentProfile(userId);
        return score(title,
                profileService.vectorOf(profile),
                profileService.confidencesOf(profile),
                profileService.meanConfidence(profile));
    }

    /**
     * Scores a title against an arbitrary target vector rather than a user's
     * profile — used by semantic search, where the "target" is parsed from the
     * query text instead of looked up from a user id.
     */
    public MovieDTO scoreAgainstVector(Title title, TraitVector target, double[] confidence) {
        double meanConf = java.util.Arrays.stream(confidence).average().orElse(0.3);
        return score(title, target, confidence, meanConf);
    }

    private MovieDTO score(Title t, TraitVector user, double[] confidence, double meanConf) {
        TraitVector movie = t.traitVector();
        double featureConf = t.getFeatureConfidence() == null ? 0.10 : t.getFeatureConfidence();
        String source = t.getFeaturesSource() == null ? "DEFAULT" : t.getFeaturesSource();

        ScoringService.Scored s = scoringService.score(
                user, confidence, movie, featureConf, source, t.normalisedQuality(), meanConf);

        MovieDTO dto = new MovieDTO();
        dto.setId(t.getTmdbId() == null ? t.getId() : t.getTmdbId().longValue());
        dto.setTitleId(t.getId());
        dto.setTmdbId(t.getTmdbId());
        dto.setTitle(t.getTitle());
        dto.setOverview(t.getSynopsis());
        dto.setPosterPath(t.getPoster());
        dto.setType(t.getType());
        dto.setYear(t.getYear());
        dto.setVoteAverage(t.getVoteAverage());
        dto.setMatchScore(s.score());
        dto.setExplanation(s.explanation());
        dto.setStoryVector(movie.toKeyedMap());
        dto.setFeatureConfidence(featureConf);
        dto.setFeaturesSource(source);
        dto.setReasons(flatReasons(s));
        return dto;
    }

    /**
     * Flat strings for the existing feed UI. Signed and quantified rather than the
     * old binary "comfort vibe matches" booleans, which carried no magnitude and so
     * could not distinguish a trait that contributed 30 points from one that
     * contributed 1.
     */
    private List<String> flatReasons(ScoringService.Scored s) {
        List<String> out = new ArrayList<>(5);
        for (TraitContribution c : s.explanation().getDrivers()) {
            out.add(String.format("+%.1f %s", c.getContribution(), c.getLabel()));
        }
        for (TraitContribution c : s.explanation().getTensions()) {
            out.add(String.format("%.1f %s", c.getContribution(), c.getLabel()));
        }
        if (out.isEmpty()) {
            out.add("Balanced fit across your profile");
        }
        return out;
    }

    /**
     * Maximal Marginal Relevance: greedily prefer titles that are both well-matched
     * and unlike what has already been picked, so the feed is not ten variations of
     * the same film.
     *
     * Re-orders ONLY. The displayed score is never adjusted for diversity — doing so
     * would make the printed explanation disagree with the printed number.
     */
    private List<MovieDTO> mmrRerank(List<MovieDTO> scored, int limit) {
        List<MovieDTO> pool = new ArrayList<>(scored.subList(0, Math.min(scored.size(), limit * 3)));
        List<MovieDTO> picked = new ArrayList<>(limit);
        List<TraitVector> pickedVecs = new ArrayList<>(limit);

        while (!pool.isEmpty() && picked.size() < limit) {
            int bestIdx = 0;
            double bestValue = -Double.MAX_VALUE;

            for (int i = 0; i < pool.size(); i++) {
                MovieDTO cand = pool.get(i);
                TraitVector v = TraitVector.fromMap(keyedToEnum(cand.getStoryVector()));

                double maxSim = 0.0;
                for (TraitVector p : pickedVecs) {
                    maxSim = Math.max(maxSim, v.centredCosine(p));
                }
                double value = cand.getMatchScore() - (DIVERSITY_LAMBDA * 100.0 * maxSim);
                if (value > bestValue) {
                    bestValue = value;
                    bestIdx = i;
                }
            }

            MovieDTO chosen = pool.remove(bestIdx);
            picked.add(chosen);
            pickedVecs.add(TraitVector.fromMap(keyedToEnum(chosen.getStoryVector())));
        }
        return picked;
    }

    private Map<Trait, Double> keyedToEnum(Map<String, Double> keyed) {
        Map<Trait, Double> out = new java.util.EnumMap<>(Trait.class);
        if (keyed == null) {
            return out;
        }
        keyed.forEach((k, v) -> {
            Trait t = Trait.fromKey(k);
            if (t != null) {
                out.put(t, v);
            }
        });
        return out;
    }
}
