package com.rewatch.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.model.Rating;
import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitEvent;
import com.rewatch.model.TraitNode;
import com.rewatch.model.TraitVector;
import com.rewatch.model.User;
import com.rewatch.model.UserTrait;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.TraitEventRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.repository.UserTraitRepository;

/**
 * Owns the user's taste profile.
 *
 * CORE DESIGN DECISION: the profile is a pure function of the ordered rating log,
 * recomputed by replay, rather than a mutable value updated in place.
 *
 * That buys three things which the incremental approach cannot give:
 *   - Re-rating a title is trivially correct; there is no clamped EMA step to
 *     reverse.
 *   - Fixing the lexicon or the weighting is a recompute, not a data migration.
 *   - The evolution timeline falls out for free, because replay emits the same
 *     TraitEvents it would have emitted live.
 *
 * At ~50 ratings x 10 traits a full replay is microseconds, so the cost is nil.
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    /** Largest single EMA step, before sign and relevance scaling. */
    private static final double BASE_STEP = 0.25;

    /** Hard cap on any one rating's influence on any one trait. */
    private static final double MAX_STEP = 0.35;

    /** How fast steps shrink as evidence accumulates, so the profile settles. */
    private static final double SETTLE_RATE = 0.02;

    /** Package-visible — Recommender.coldStartSafeReorder reads this directly rather than duplicating the value, so the two can't silently drift apart. */
    static final double ONBOARDING_CONFIDENCE = 0.35;

    private final RatingRepository ratingRepo;
    private final TitleRepository titleRepo;
    private final UserTraitRepository userTraitRepo;
    private final TraitEventRepository traitEventRepo;
    private final UserRepository userRepo;
    private final VectorEngine vectorEngine;

    public ProfileService(RatingRepository ratingRepo,
                          TitleRepository titleRepo,
                          UserTraitRepository userTraitRepo,
                          TraitEventRepository traitEventRepo,
                          UserRepository userRepo,
                          VectorEngine vectorEngine) {
        this.ratingRepo = ratingRepo;
        this.titleRepo = titleRepo;
        this.userTraitRepo = userTraitRepo;
        this.traitEventRepo = traitEventRepo;
        this.userRepo = userRepo;
        this.vectorEngine = vectorEngine;
    }

    /** One trait's movement caused by a single rating. */
    public record Shift(Trait trait, double before, double after, double delta, double confidence) {}

    // ---------------------------------------------------------------- reads

    /** Current profile, materialised. Falls back to the neutral profile. */
    public Map<Trait, TraitNode> currentProfile(Long userId) {
        List<UserTrait> rows = userTraitRepo.findByUserId(userId);
        Map<Trait, TraitNode> profile = neutralProfile(Instant.now());
        for (UserTrait row : rows) {
            profile.put(row.getTrait(), row.toNode());
        }
        return profile;
    }

    public TraitVector vectorOf(Map<Trait, TraitNode> profile) {
        Map<Trait, Double> vals = new EnumMap<>(Trait.class);
        profile.forEach((t, node) -> vals.put(t, node.getVal()));
        return TraitVector.fromMap(vals);
    }

    public double[] confidencesOf(Map<Trait, TraitNode> profile) {
        double[] out = new double[Trait.count()];
        for (Trait t : Trait.values()) {
            TraitNode node = profile.get(t);
            out[t.ordinal()] = node == null ? 0.05 : node.getConf();
        }
        return out;
    }

    public double meanConfidence(Map<Trait, TraitNode> profile) {
        double sum = 0;
        for (Trait t : Trait.values()) {
            TraitNode node = profile.get(t);
            sum += node == null ? 0.05 : node.getConf();
        }
        return sum / Trait.count();
    }

    // ---------------------------------------------------------------- writes

    /**
     * Recomputes the whole profile from the rating log and rewrites both the
     * materialised traits and the event history.
     *
     * @return the shifts caused by the most recent rating, for the "your TasteDNA
     *         just moved" response
     */
    @Transactional
    public List<Shift> replay(Long userId) {
        Map<Trait, TraitNode> profile = seedProfile(userId);

        traitEventRepo.deleteByUserId(userId);

        List<Rating> ratings = ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(userId);
        Map<Long, Title> titleCache = loadTitles(ratings);

        List<TraitEvent> events = new ArrayList<>();
        List<Shift> lastShifts = new ArrayList<>();

        // Seed events give the timeline a real starting point rather than a line
        // that springs into existence at the first rating.
        Instant seedAt = ratings.isEmpty()
                ? Instant.now()
                : ratings.get(0).getCreatedAt().minusSeconds(1);
        for (Trait t : Trait.values()) {
            TraitNode seeded = profile.get(t);
            events.add(new TraitEvent(userId, t, TraitNode.unknown(seedAt), seeded,
                    TraitEvent.Source.ONBOARDING, null, seedAt));
        }

        for (Rating rating : ratings) {
            Title title = titleCache.get(rating.getTitleId());
            if (title == null) {
                continue;
            }
            TraitVector movie = title.traitVector();
            double featureConf = title.getFeatureConfidence() == null
                    ? 0.10 : title.getFeatureConfidence();

            List<Shift> shifts = new ArrayList<>();
            for (Trait t : Trait.values()) {
                TraitNode before = profile.get(t);
                double weight = weightFor(t, rating, movie, featureConf, before);
                TraitNode after = vectorEngine.updatetrait(
                        before, movie.get(t), weight, rating.getCreatedAt());

                profile.put(t, after);
                events.add(new TraitEvent(userId, t, before, after,
                        TraitEvent.Source.RATING, rating.getId(), rating.getCreatedAt()));
                shifts.add(new Shift(t, before.getVal(), after.getVal(),
                        after.getVal() - before.getVal(), after.getConf()));
            }
            lastShifts = shifts;
        }

        traitEventRepo.saveAll(events);
        persistProfile(userId, profile);

        log.debug("Replayed {} ratings for user {} into {} trait events",
                ratings.size(), userId, events.size());

        return lastShifts;
    }

    /**
     * The per-trait EMA step for one rating.
     *
     * RELEVANCE IS THE LOAD-BEARING TERM. Without it, every rating drags every
     * trait toward that movie's value including the axes the movie says nothing
     * about (which sit at 0.5). After ~20 ratings every user's profile collapses to
     * a uniform 0.5 mush — the exact opposite of "your taste evolved".
     *
     * featureConfidence does the same job across titles: a genre-only movie should
     * move the profile far less than a fully enriched one.
     *
     * The settle term turns a fixed-alpha EMA (which oscillates forever and never
     * converges) into a proper running estimate that stabilises as evidence grows.
     */
    private double weightFor(Trait trait, Rating rating, TraitVector movie,
                             double featureConf, TraitNode current) {

        double facetScore = facetScoreFor(trait, rating);
        double signed = (facetScore - 3.0) / 2.0;                 // -1 .. +1
        double relevance = 2.0 * Math.abs(movie.get(trait) - TraitVector.NEUTRAL);
        double settle = 1.0 / (1.0 + SETTLE_RATE * current.getEvid());

        double w = BASE_STEP * signed * relevance * featureConf * settle;
        return Math.max(-MAX_STEP, Math.min(MAX_STEP, w));
    }

    /**
     * Blends the overall star rating with the facets that actually say something
     * about this particular trait. Facets the user left blank are skipped.
     */
    private double facetScoreFor(Trait trait, Rating r) {
        double overall = r.getOverall() == null ? 3.0 : r.getOverall();

        List<Integer> routed = new ArrayList<>(2);
        switch (trait) {
            case GROWTH    -> { add(routed, r.getChars()); add(routed, r.getStory()); }
            case FAMILY    -> add(routed, r.getChars());
            case ROMANCE   -> add(routed, r.getChars());
            case HOPE      -> add(routed, r.getEnding());
            case BITTER    -> add(routed, r.getEnding());
            case INTENSITY -> add(routed, r.getVisuals());
            case NOSTALGIA -> add(routed, r.getVisuals());
            case PACING    -> add(routed, r.getStory());
            case COMFORT   -> add(routed, r.getRewatch());
            case HUMOR     -> add(routed, r.getRewatch());
        }

        if (routed.isEmpty()) {
            return overall;
        }
        double facetMean = routed.stream().mapToInt(Integer::intValue).average().orElse(overall);
        return 0.5 * overall + 0.5 * facetMean;
    }

    private void add(List<Integer> list, Integer v) {
        if (v != null) {
            list.add(v);
        }
    }

    @Transactional
    public void seedFromOnboarding(Long userId, TraitVector seed, List<String> dealbreakers) {
        userRepo.findById(userId).ifPresent(user -> {
            user.setSeedVector(encode(seed));
            if (dealbreakers != null && !dealbreakers.isEmpty()) {
                user.setDealbreakers(String.join(",", dealbreakers));
            }
            userRepo.save(user);
        });
        replay(userId);
    }

    // ---------------------------------------------------------------- internals

    private Map<Trait, TraitNode> seedProfile(Long userId) {
        Instant now = Instant.now();
        User user = userRepo.findById(userId).orElse(null);
        TraitVector seed = user == null ? null : decode(user.getSeedVector());

        if (seed == null) {
            return neutralProfile(now);
        }

        Map<Trait, TraitNode> profile = new EnumMap<>(Trait.class);
        for (Trait t : Trait.values()) {
            profile.put(t, new TraitNode(seed.get(t), ONBOARDING_CONFIDENCE, 0, 0, now));
        }
        return profile;
    }

    private Map<Trait, TraitNode> neutralProfile(Instant at) {
        Map<Trait, TraitNode> profile = new EnumMap<>(Trait.class);
        for (Trait t : Trait.values()) {
            profile.put(t, TraitNode.unknown(at));
        }
        return profile;
    }

    private void persistProfile(Long userId, Map<Trait, TraitNode> profile) {
        Map<Trait, UserTrait> existing = new EnumMap<>(Trait.class);
        for (UserTrait row : userTraitRepo.findByUserId(userId)) {
            existing.put(row.getTrait(), row);
        }

        List<UserTrait> toSave = new ArrayList<>(Trait.count());
        for (Trait t : Trait.values()) {
            UserTrait row = existing.get(t);
            if (row == null) {
                row = new UserTrait(userId, t, profile.get(t));
            } else {
                row.applyNode(profile.get(t));
            }
            toSave.add(row);
        }
        userTraitRepo.saveAll(toSave);
    }

    private Map<Long, Title> loadTitles(List<Rating> ratings) {
        Map<Long, Title> out = new HashMap<>();
        if (ratings.isEmpty()) {
            return out;
        }
        List<Long> ids = ratings.stream().map(Rating::getTitleId).distinct().toList();
        for (Title t : titleRepo.findAllById(ids)) {
            out.put(t.getId(), t);
        }
        return out;
    }

    static String encode(TraitVector v) {
        StringBuilder sb = new StringBuilder();
        double[] arr = v.toArray();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(java.util.Locale.ROOT, "%.4f", arr[i]));
        }
        return sb.toString();
    }

    static TraitVector decode(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        String[] parts = csv.split(",");
        if (parts.length != Trait.count()) {
            return null;
        }
        double[] vals = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                vals[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return TraitVector.of(vals);
    }
}
