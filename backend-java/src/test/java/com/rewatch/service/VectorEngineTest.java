package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.rewatch.model.TraitNode;
import com.rewatch.model.TraitVector;

/**
 * VectorEngine.updatetrait is the primitive every rating's effect on the
 * profile ultimately goes through — kept deliberately pure (no I/O) so it can
 * be tested directly rather than only indirectly through a full replay.
 */
class VectorEngineTest {

    private final VectorEngine engine = new VectorEngine();

    @Test
    void positiveWeightPullsValueTowardMovie() {
        TraitNode before = new TraitNode(0.5, 0.10, 0, 2, Instant.EPOCH);
        TraitNode after = engine.updatetrait(before, 0.9, 0.2, Instant.now());

        assertTrue(after.getVal() > before.getVal(), "a positive weight must move the trait toward the movie's value");
        assertTrue(after.getVal() < 0.9, "the step is an EMA, not a jump straight to the movie's value");
    }

    @Test
    void negativeWeightReflectsValueAwayFromMovie() {
        // The push-away case needs no special handling: with a negative weight the
        // update becomes old + |w|*(old - movieval), an exact reflection.
        TraitNode before = new TraitNode(0.5, 0.10, 0, 2, Instant.EPOCH);
        TraitNode after = engine.updatetrait(before, 0.9, -0.2, Instant.now());

        assertTrue(after.getVal() < before.getVal(),
                "a negative weight (a dislike) must move the trait AWAY from the movie's value, not toward it");
    }

    @Test
    void confidenceRisesRegardlessOfLikeOrDislike() {
        TraitNode before = new TraitNode(0.5, 0.20, 0, 2, Instant.EPOCH);

        TraitNode afterLike = engine.updatetrait(before, 0.9, 0.2, Instant.now());
        TraitNode afterDislike = engine.updatetrait(before, 0.9, -0.2, Instant.now());

        assertTrue(afterLike.getConf() > before.getConf(), "confidence must rise after a like");
        assertTrue(afterDislike.getConf() > before.getConf(), "confidence must rise after a dislike too — disliking something is just as informative");
        assertEquals(afterLike.getConf(), afterDislike.getConf(), 1e-9,
                "confidence depends only on evidence count, not on the sign of the update");
    }

    @Test
    void evidenceCurveMatchesNOverNPlusK() {
        // K=8: the number of ratings at which confidence should sit at 50%.
        TraitNode zero = TraitNode.unknown(Instant.EPOCH);
        TraitNode afterEight = zero;
        for (int i = 0; i < 8; i++) {
            afterEight = engine.updatetrait(afterEight, 0.7, 0.1, Instant.now());
        }
        assertEquals(8.0 / (8.0 + 8.0), afterEight.getConf(), 0.01,
                "after exactly K=8 pieces of evidence, confidence should be ~0.5");
    }

    @Test
    void resultIsAlwaysClampedToValidRange() {
        TraitNode before = new TraitNode(0.95, 0.10, 0, 0, Instant.EPOCH);
        // A weight at the extreme end plus a movie value of 1.0 must never push
        // the result outside [0,1], regardless of how the arithmetic works out.
        TraitNode after = engine.updatetrait(before, 1.0, 0.35, Instant.now());
        assertTrue(after.getVal() <= TraitVector.clamp(after.getVal()) + 1e-9 && after.getVal() >= 0.0 && after.getVal() <= 1.0);
    }

    @Test
    void unknownNodeSeedsDirectlyFromFirstObservation() {
        TraitNode after = engine.updatetrait(null, 0.7, 0.2, Instant.now());
        assertEquals(0.7, after.getVal(), 1e-9, "the very first observation for a trait becomes its seed value");
        assertEquals(1, after.getEvid());
    }
}
