package com.rewatch.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.rewatch.dto.MovieDTO;
import com.rewatch.dto.TraitContribution;
import com.rewatch.model.Rating;
import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitNode;
import com.rewatch.model.TraitVector;
import com.rewatch.model.User;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.UserRepository;

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

    /**
     * A dealbreaker only gets a real hard-filter entry here if a title's own
     * TraitVector can honestly signal it — checked against exactly what
     * OnboardingService.DEALBREAKER_SENTIMENT already routes each one to.
     * "Love Triangle", "Animal Death", and "Cheating" have no per-title proxy
     * (ROMANCE/BITTER alone can't tell a triangle from any other romance, or
     * an animal death from any other bittersweet beat) — those stay a soft
     * trait nudge only rather than a fake heuristic, same reasoning as
     * dropping onboarding's Visual Style screen and the Runtime field.
     */
    /**
     * Below this, a title is common enough in TMDB's own catalog to have basically
     * no real audience signal — the kind of thing that lets a 4,000-episode regional
     * daytime soap or a barely-catalogued local talk show slip into "Tonight's Pick"
     * on a raw keyword-genre match. Not a hard cutoff by itself — see {@link
     * #candidatePool}, which falls back to the full renderable set if too few
     * titles clear this bar, so a thin catalog (e.g. anime/kdrama right now) never
     * gets starved by a threshold tuned for a much bigger one.
     */
    private static final int MIN_VOTE_COUNT_FOR_RECOMMENDATION = 150;

    private static final Map<String, Predicate<TraitVector>> DEALBREAKER_THRESHOLDS = new HashMap<>();
    static {
        DEALBREAKER_THRESHOLDS.put("excessive gore", v -> v.get(Trait.INTENSITY) >= 0.75);
        DEALBREAKER_THRESHOLDS.put("jump scares", v -> v.get(Trait.INTENSITY) >= 0.75);
        DEALBREAKER_THRESHOLDS.put("sad ending", v -> v.get(Trait.BITTER) >= 0.7 && v.get(Trait.HOPE) <= 0.4);
        DEALBREAKER_THRESHOLDS.put("open ending", v -> v.get(Trait.BITTER) >= 0.65);
        DEALBREAKER_THRESHOLDS.put("unresolved ending", v -> v.get(Trait.BITTER) >= 0.65);
        DEALBREAKER_THRESHOLDS.put("slow start", v -> v.get(Trait.PACING) >= 0.75);
    }

    private final TitleRepository titleRepo;
    private final RatingRepository ratingRepo;
    private final UserRepository userRepo;
    private final ProfileService profileService;
    private final ScoringService scoringService;

    public Recommender(TitleRepository titleRepo,
                       RatingRepository ratingRepo,
                       UserRepository userRepo,
                       ProfileService profileService,
                       ScoringService scoringService) {
        this.titleRepo = titleRepo;
        this.ratingRepo = ratingRepo;
        this.userRepo = userRepo;
        this.profileService = profileService;
        this.scoringService = scoringService;
    }

    public List<MovieDTO> recommend(Long userId, int limit, boolean excludeRated, boolean diversify) {
        List<Title> candidates = candidatePool(limit);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Trait, TraitNode> profile = profileService.currentProfile(userId);
        TraitVector user = profileService.vectorOf(profile);
        double[] confidence = profileService.confidencesOf(profile);
        double meanConf = profileService.meanConfidence(profile);

        Set<Long> ratedTitleIds = ratedIds(userId);
        Set<String> dealbreakers = dealbreakersFor(userId);

        List<MovieDTO> scored = new ArrayList<>(candidates.size());
        for (Title t : candidates) {
            boolean isRated = ratedTitleIds.contains(t.getId());
            if (excludeRated && isRated) {
                continue;
            }
            // Hard-filtered here, not just scored down — a dealbreaker is an
            // explicit "never show me this," not a soft preference. Applied
            // to the main feed only (not every Discovery row) for now.
            if (!passesDealbreakers(t, dealbreakers)) {
                continue;
            }
            MovieDTO dto = score(t, user, confidence, meanConf);
            dto.setRated(isRated);
            scored.add(dto);
        }

        scored.sort(Comparator.comparingDouble(MovieDTO::getMatchScore).reversed());

        List<MovieDTO> ranked = (diversify && scored.size() > limit)
                ? mmrRerank(scored, limit)
                : scored.subList(0, Math.min(limit, scored.size()));
        return coldStartSafeReorder(ranked, meanConf);
    }

    /**
     * The candidate set for {@link #recommend}: real, renderable titles, biased
     * toward ones with actual audience signal — but never at the cost of starving
     * the feed. A title missing a synopsis or poster is broken data and is dropped
     * unconditionally (no card should ever render with nothing in it); the vote-count
     * bias only applies if enough titles clear it to still fill {@code limit * 3}
     * (the same pool size {@link #mmrRerank} already draws from).
     */
    /** Package-private for direct testing — see RecommenderTest. */
    List<Title> candidatePool(int limit) {
        List<Title> renderable = titleRepo.findAll().stream()
                .filter(this::hasRenderableData)
                .toList();
        List<Title> wellKnown = renderable.stream()
                .filter(t -> t.getVoteCount() != null && t.getVoteCount() >= MIN_VOTE_COUNT_FOR_RECOMMENDATION)
                .toList();
        return wellKnown.size() >= limit * 3 ? wellKnown : renderable;
    }

    private boolean hasRenderableData(Title t) {
        return t.getSynopsis() != null && !t.getSynopsis().isBlank()
                && t.getPoster() != null && !t.getPoster().isBlank();
    }

    /** How many titles the user's dealbreakers are currently hiding from recommend(). */
    public int dealbreakerHiddenCount(Long userId) {
        Set<String> dealbreakers = dealbreakersFor(userId);
        if (dealbreakers.isEmpty()) {
            return 0;
        }
        Set<Long> ratedTitleIds = ratedIds(userId);
        return (int) titleRepo.findAll().stream()
                .filter(t -> !ratedTitleIds.contains(t.getId()))
                .filter(t -> !passesDealbreakers(t, dealbreakers))
                .count();
    }

    /** The titles a dealbreaker is currently hiding, scored and ranked the same way recommend() would. */
    public List<MovieDTO> hiddenByDealbreakers(Long userId, int limit) {
        Set<String> dealbreakers = dealbreakersFor(userId);
        if (dealbreakers.isEmpty()) {
            return List.of();
        }

        Map<Trait, TraitNode> profile = profileService.currentProfile(userId);
        TraitVector user = profileService.vectorOf(profile);
        double[] confidence = profileService.confidencesOf(profile);
        double meanConf = profileService.meanConfidence(profile);
        Set<Long> ratedTitleIds = ratedIds(userId);

        List<MovieDTO> hidden = new ArrayList<>();
        for (Title t : titleRepo.findAll()) {
            if (ratedTitleIds.contains(t.getId()) || passesDealbreakers(t, dealbreakers)) {
                continue;
            }
            hidden.add(score(t, user, confidence, meanConf));
        }
        hidden.sort(Comparator.comparingDouble(MovieDTO::getMatchScore).reversed());
        return hidden.subList(0, Math.min(limit, hidden.size()));
    }

    /** Package-private for direct testing — see RecommenderTest. */
    boolean passesDealbreakers(Title t, Set<String> dealbreakers) {
        if (dealbreakers.isEmpty()) {
            return true;
        }
        TraitVector v = t.traitVector();
        for (String d : dealbreakers) {
            Predicate<TraitVector> check = DEALBREAKER_THRESHOLDS.get(d);
            if (check != null && check.test(v)) {
                return false;
            }
        }
        return true;
    }

    private Set<String> dealbreakersFor(Long userId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null || user.getDealbreakers() == null || user.getDealbreakers().isBlank()) {
            return Set.of();
        }
        return Arrays.stream(user.getDealbreakers().split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(java.util.Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private Set<Long> ratedIds(Long userId) {
        Set<Long> ids = new HashSet<>();
        for (Rating r : ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(userId)) {
            ids.add(r.getTitleId());
        }
        return ids;
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
        dto.setOriginalLanguage(t.getOriginalLanguage());
        dto.setGenres(com.rewatch.features.GenreLexicon.namesFor(t.getGenreIds()));
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
     * ProfileService seeds every trait at exactly ONBOARDING_CONFIDENCE (0.35)
     * the moment onboarding finishes, regardless of how many real answers were
     * given — a skip and a fully-answered wizard both land here. Confidence can
     * briefly dip *below* this once ratings start (VectorEngine's n/(n+8) curve
     * restarts a touched trait's evidence count from 1), so meanConfidence is
     * not monotonic — it does not simply climb from 0 as the case study's
     * general framing might suggest. The threshold must therefore be strictly
     * greater-than 0.35, not >=: a `>=` comparison treats "just finished
     * onboarding, zero ratings" — the exact case this exists to catch — as
     * already trustworthy, which is how this shipped once already without
     * ever actually engaging (verified live: a fresh account's mean confidence
     * sat at precisely 0.35 and the swap never fired).
     *
     * Below this, a raw top-match-score pick for the single featured "Tonight's
     * Pick" card can be whatever the thinnest signal happened to favor — which
     * showed up in practice as a fresh account's #1 card being a tonally
     * intense, niche pick ahead of far more broadly-appealing titles it was
     * matched almost as well against. The match isn't wrong, but leading a
     * brand-new user's first screen with it is a bad first impression the low
     * confidence itself should have been a signal to avoid.
     */
    private static final double COLD_START_CONFIDENCE_THRESHOLD = ProfileService.ONBOARDING_CONFIDENCE;
    private static final double COLD_START_INTENSITY_CEILING = 0.6;
    private static final double COLD_START_MIN_VOTE_AVERAGE = 6.5;
    // INTENSITY alone tracks action/violence-style intensity (Trait.java), which
    // let a genuinely heavy pick through untouched (grief, abuse, loss — low
    // action-intensity, still a hard first impression). BITTER (labelled
    // "Bittersweet Drama") is the axis that actually tracks tonal heaviness, so
    // both are gated.
    private static final double COLD_START_BITTER_CEILING = 0.6;
    private static final int COLD_START_LOOKAHEAD = 6;

    /**
     * Promotes the first broadly-appealing title within the top {@link
     * #COLD_START_LOOKAHEAD} results to the front, when the profile is still
     * cold. Re-orders ONLY — same rule as {@link #mmrRerank}: the displayed
     * score is never touched, so what's promoted is still a real, already-ranked
     * match, just not necessarily the single highest-scoring one. A no-op once
     * enough ratings exist to trust the ranking as-is.
     */
    private List<MovieDTO> coldStartSafeReorder(List<MovieDTO> ranked, double meanConf) {
        if (meanConf > COLD_START_CONFIDENCE_THRESHOLD || ranked.size() < 2) {
            return ranked;
        }
        int lookahead = Math.min(COLD_START_LOOKAHEAD, ranked.size());
        int safeIdx = -1;
        for (int i = 0; i < lookahead; i++) {
            MovieDTO cand = ranked.get(i);
            TraitVector v = TraitVector.fromMap(keyedToEnum(cand.getStoryVector()));
            Double voteAvg = cand.getVoteAverage();
            boolean broadlyAppealing = v.get(Trait.INTENSITY) <= COLD_START_INTENSITY_CEILING
                    && v.get(Trait.BITTER) <= COLD_START_BITTER_CEILING
                    && (voteAvg == null || voteAvg >= COLD_START_MIN_VOTE_AVERAGE);
            if (broadlyAppealing) {
                safeIdx = i;
                break;
            }
        }
        if (safeIdx <= 0) {
            // Already leading with a safe pick, or nothing in the lookahead
            // qualifies — leave the score-based ranking alone rather than
            // forcing a swap that has nothing better to offer.
            return ranked;
        }
        List<MovieDTO> reordered = new ArrayList<>(ranked);
        MovieDTO promoted = reordered.remove(safeIdx);
        reordered.add(0, promoted);
        return reordered;
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
