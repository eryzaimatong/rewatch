package com.rewatch.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitNode;
import com.rewatch.model.TraitVector;
import com.rewatch.model.User;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.UserRepository;

/**
 * "How compatible is your taste with a friend's?" — the no-signup half of
 * the social loop. A visitor with no account rates a fixed set of well-known
 * titles (the "quiz"), which builds a real, if noisy, trait vector using the
 * exact same VectorEngine primitive that powers every real rating in the
 * app — never a separate, fake scoring path — compared against the target
 * user's actual current profile via the same centred-cosine metric
 * SocialService already uses for logged-in DNA matches.
 *
 * Deliberately stateless: nothing here writes a Rating row (the visitor has
 * no account to attach one to) or persists anything at all — the vector is
 * built, compared, and discarded within one request.
 */
@Service
public class CompatibilityService {

    /** Below this, the target's own profile is too thin to compare against honestly — matches SocialService's floor. */
    public static final int MIN_TARGET_RATINGS = 3;
    public static final int QUIZ_SIZE = 8;
    /** Candidate pool the quiz is greedily diversified from — large enough for real spread, small enough to stay fast. */
    private static final int QUIZ_CANDIDATE_POOL = 150;
    private static final int QUIZ_MIN_VOTE_COUNT = 150;

    private final TitleRepository titleRepo;
    private final UserRepository userRepo;
    private final RatingRepository ratingRepo;
    private final ProfileService profileService;
    private final VectorEngine vectorEngine;

    public CompatibilityService(TitleRepository titleRepo, UserRepository userRepo, RatingRepository ratingRepo,
                                ProfileService profileService, VectorEngine vectorEngine) {
        this.titleRepo = titleRepo;
        this.userRepo = userRepo;
        this.ratingRepo = ratingRepo;
        this.profileService = profileService;
        this.vectorEngine = vectorEngine;
    }

    private volatile List<Title> cachedQuiz;

    public record QuizResponse(Long titleId, Integer overall) {}
    public record CompatibilityResult(String targetUsername, int compatibilityPercent,
                                       List<String> sharedTraits, String headline) {}

    /** The fixed 8-title quiz, computed once and cached — see class doc for why it's diversity-greedy, not random. */
    public List<Title> quiz() {
        List<Title> quiz = cachedQuiz;
        if (quiz == null) {
            quiz = buildQuiz();
            cachedQuiz = quiz;
        }
        return quiz;
    }

    private List<Title> buildQuiz() {
        List<Title> pool = titleRepo.findAll().stream()
                .filter(t -> t.getSynopsis() != null && !t.getSynopsis().isBlank())
                .filter(t -> t.getPoster() != null && !t.getPoster().isBlank())
                .filter(t -> t.getVoteCount() != null && t.getVoteCount() >= QUIZ_MIN_VOTE_COUNT)
                .sorted(Comparator.comparingDouble(Title::getPopularity).reversed())
                .limit(QUIZ_CANDIDATE_POOL)
                .toList();
        if (pool.isEmpty()) {
            return List.of();
        }

        List<Title> selected = new ArrayList<>(QUIZ_SIZE);
        selected.add(pool.get(0));
        while (selected.size() < QUIZ_SIZE && selected.size() < pool.size()) {
            Title best = null;
            double bestMinDistance = -1;
            for (Title candidate : pool) {
                if (selected.contains(candidate)) {
                    continue;
                }
                double minDistance = Double.MAX_VALUE;
                for (Title already : selected) {
                    minDistance = Math.min(minDistance, euclideanDistance(candidate.traitVector(), already.traitVector()));
                }
                if (minDistance > bestMinDistance) {
                    bestMinDistance = minDistance;
                    best = candidate;
                }
            }
            selected.add(best);
        }
        return selected;
    }

    private double euclideanDistance(TraitVector a, TraitVector b) {
        double sum = 0;
        for (Trait t : Trait.values()) {
            double d = a.get(t) - b.get(t);
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    /**
     * Builds an anonymous trait vector from the visitor's quiz answers,
     * reusing VectorEngine.updatetrait — the same primitive every real
     * rating's replay uses — one axis at a time, starting each from neutral.
     * A fixed, deliberately strong step (0.35) rather than
     * ProfileService.weightFor's full formula: there's no accumulated
     * evidence/settle state to decay against for a one-shot, 8-response
     * session, and a strong step is what makes 8 answers actually move the
     * vector somewhere legible.
     */
    // Stronger than a real rating's typical step (see ProfileService.weightFor)
    // on purpose: a visitor only answers 8 questions total, not a growing
    // rating history, so each answer needs to move the needle decisively
    // for the result to feel confident instead of muted. Tuned so a single
    // maximally-relevant 5-star answer clears sharedTraits()'s 0.15 movement
    // threshold on its own — verified in CompatibilityServiceTest.
    private static final double QUIZ_STEP = 0.5;

    private TraitVector vectorFromQuizResponses(List<QuizResponse> responses, Map<Long, Title> quizById) {
        java.util.EnumMap<Trait, TraitNode> nodes = new java.util.EnumMap<>(Trait.class);
        Instant now = Instant.now();
        for (Trait t : Trait.values()) {
            nodes.put(t, TraitNode.unknown(now));
        }

        for (QuizResponse r : responses) {
            Title title = quizById.get(r.titleId());
            if (title == null || r.overall() == null) {
                continue;
            }
            TraitVector movie = title.traitVector();
            double signed = (r.overall() - 3.0) / 2.0;
            for (Trait t : Trait.values()) {
                double relevance = 2.0 * Math.abs(movie.get(t) - TraitVector.NEUTRAL);
                double weight = Math.max(-0.9, Math.min(0.9, QUIZ_STEP * signed * relevance));
                nodes.put(t, vectorEngine.updatetrait(nodes.get(t), movie.get(t), weight, now));
            }
        }

        double[] raw = new double[Trait.values().length];
        for (Trait t : Trait.values()) {
            raw[t.ordinal()] = nodes.get(t).getVal();
        }
        return TraitVector.of(raw);
    }

    /**
     * @return null if the target username doesn't exist, isn't public, or
     *         doesn't have enough of their own rating history to compare
     *         against honestly (see MIN_TARGET_RATINGS).
     */
    public CompatibilityResult check(String targetUsername, List<QuizResponse> responses) {
        User target = userRepo.findByUsername(targetUsername);
        if (target == null || !target.isProfilePublic()) {
            return null;
        }
        if (ratingRepo.countByUserId(target.getId()) < MIN_TARGET_RATINGS) {
            return null;
        }

        Map<Long, Title> quizById = new java.util.HashMap<>();
        quiz().forEach(t -> quizById.put(t.getId(), t));

        TraitVector visitorVector = vectorFromQuizResponses(responses, quizById);
        TraitVector targetVector = profileService.vectorOf(profileService.currentProfile(target.getId()));

        double cosine = visitorVector.centredCosine(targetVector);
        int percent = (int) Math.round(((cosine + 1) / 2) * 100);

        List<String> shared = sharedTraits(visitorVector, targetVector);
        String headline = headlineFor(percent);

        return new CompatibilityResult(targetUsername, percent, shared, headline);
    }

    private List<String> sharedTraits(TraitVector a, TraitVector b) {
        return java.util.Arrays.stream(Trait.values())
                .filter(t -> Math.abs(a.get(t) - TraitVector.NEUTRAL) >= 0.15)
                .filter(t -> Math.abs(b.get(t) - TraitVector.NEUTRAL) >= 0.15)
                .filter(t -> (a.get(t) - TraitVector.NEUTRAL) * (b.get(t) - TraitVector.NEUTRAL) > 0)
                .sorted(Comparator.comparingDouble((Trait t) ->
                        -Math.min(Math.abs(a.get(t) - TraitVector.NEUTRAL), Math.abs(b.get(t) - TraitVector.NEUTRAL))))
                .limit(3)
                .map(Trait::label)
                .toList();
    }

    private String headlineFor(int percent) {
        if (percent >= 90) return "Certified taste twins.";
        if (percent >= 75) return "Scarily compatible.";
        if (percent >= 55) return "Solidly on the same wavelength.";
        if (percent >= 35) return "Different tastes, could still work.";
        return "Wildly different palates. Bold of you to check.";
    }
}
