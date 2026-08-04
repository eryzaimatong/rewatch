package com.rewatch.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.rewatch.model.TraitNode;
import com.rewatch.model.TraitVector;

/**
 * The primitive that moves a single trait in response to one piece of evidence.
 *
 * Deliberately kept tiny and pure: no I/O, no repositories, no clock. That makes
 * the taste model fully unit-testable and makes replay deterministic.
 */
@Service
public class VectorEngine {

    /**
     * Evidence prior for the confidence curve. Confidence is n/(n+K), so K is the
     * number of ratings at which we are 50% confident. The previous formula
     * 1-1/(n+1) is this with K=1, which saturates almost immediately (0.83 after
     * five ratings) and so carried very little information.
     */
    private static final double EVIDENCE_PRIOR = 8.0;

    private static final double MAX_CONFIDENCE = 0.99;

    /** Movement smaller than this is reported as "stable" rather than up/down. */
    private static final double TREND_EPSILON = 0.01;

    /**
     * Exponential-moving-average step toward (or away from) an observed value.
     *
     * The sign of {@code weight} carries like/dislike:
     *   weight > 0  pulls the trait toward movieval  (the user enjoyed this)
     *   weight < 0  reflects it away from movieval   (the user did not)
     *
     * The push-away case needs no special handling — with negative w the update
     * becomes old + |w|*(old - movieval), an exact reflection, and the clamp below
     * absorbs any overshoot.
     *
     * Confidence rises with evidence regardless of sign: disliking something is
     * just as informative as liking it.
     */
    public TraitNode updatetrait(TraitNode oldnode, double movieval, double weight, Instant at) {
        if (oldnode == null) {
            return new TraitNode(TraitVector.clamp(movieval), 0.10, 0, 1, at);
        }

        double oldval = oldnode.getVal();
        double nextval = TraitVector.clamp((oldval * (1.0 - weight)) + (movieval * weight));

        int dir = 0;
        if (nextval - oldval > TREND_EPSILON) {
            dir = 1;
        } else if (oldval - nextval > TREND_EPSILON) {
            dir = -1;
        }

        int count = oldnode.getEvid() + 1;
        double nextconf = Math.min(count / (count + EVIDENCE_PRIOR), MAX_CONFIDENCE);

        return new TraitNode(nextval, nextconf, dir, count, at);
    }

    /** Confidence implied by an evidence count, without performing an update. */
    public static double confidenceFor(int evidenceCount) {
        if (evidenceCount <= 0) {
            return 0.05;
        }
        return Math.min(evidenceCount / (evidenceCount + EVIDENCE_PRIOR), MAX_CONFIDENCE);
    }
}
