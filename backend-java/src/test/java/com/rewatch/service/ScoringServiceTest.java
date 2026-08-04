package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.rewatch.dto.TraitContribution;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitVector;

/**
 * The one invariant the whole explainable-scoring feature stands on: the
 * printed per-trait contributions, plus the baseline and the quality bonus,
 * must sum EXACTLY to the printed score. If a user (or an interviewer) adds
 * the numbers on screen and gets something else, the feature has failed at
 * the one job it exists to do — see ScoringService's class doc and
 * docs/CASE-STUDY.md for why each of the three "obvious" simplifications
 * (no weight floor, non-negative contributions, no calibration) breaks this.
 */
class ScoringServiceTest {

    private ScoringService newService() {
        ScoringService svc = new ScoringService();
        // @Value fields — no Spring context needed for a pure unit test.
        ReflectionTestUtils.setField(svc, "anchor", 0.80);
        ReflectionTestUtils.setField(svc, "slope", 440.0);
        return svc;
    }

    private TraitVector randomVector(Random rnd) {
        double[] v = new double[Trait.count()];
        for (int i = 0; i < v.length; i++) {
            v[i] = rnd.nextDouble();
        }
        return TraitVector.of(v);
    }

    @RepeatedTest(200)
    void contributionsSumExactlyToTheDisplayedScore() {
        Random rnd = new Random();
        ScoringService svc = newService();

        TraitVector user = randomVector(rnd);
        TraitVector movie = randomVector(rnd);
        double[] confidence = new double[Trait.count()];
        for (int i = 0; i < confidence.length; i++) {
            confidence[i] = rnd.nextDouble();
        }
        double featureConfidence = rnd.nextDouble();
        double quality = rnd.nextDouble();
        double meanConfidence = rnd.nextDouble();

        ScoringService.Scored result = svc.score(
                user, confidence, movie, featureConfidence, "TMDB_KEYWORDS", quality, meanConfidence);

        double sumOfParts = result.explanation().getBaseline()
                + result.explanation().getQualityBonus()
                + result.explanation().getAll().stream()
                        .mapToDouble(TraitContribution::getContribution)
                        .sum();

        // Both sides are already rounded to one decimal by ScoringService; compare
        // at that same precision rather than introducing a floating-point epsilon.
        assertEquals(Math.round(result.score() * 10.0), Math.round(sumOfParts * 10.0),
                () -> "score=" + result.score() + " but parts sum to " + sumOfParts
                        + " (user=" + user + ", movie=" + movie + ")");
    }

    @Test
    void freshUserWithNeutralProfileScoresWithoutBlowingUp() {
        // The exact failure mode the weight floor exists to prevent: a brand-new
        // user's profile is neutral (0.5) on every axis, which makes `salience`
        // exactly zero everywhere. Without the floor in ScoringService, the total
        // weight collapses to zero and the score becomes NaN.
        ScoringService svc = newService();
        TraitVector neutralUser = TraitVector.neutral();
        TraitVector movie = TraitVector.of(new double[] {0.8, 0.2, 0.6, 0.4, 0.9, 0.1, 0.7, 0.3, 0.5, 0.6});
        double[] tinyConfidence = new double[Trait.count()];
        java.util.Arrays.fill(tinyConfidence, 0.05);

        ScoringService.Scored result = svc.score(
                neutralUser, tinyConfidence, movie, 0.5, "GENRE_ONLY", 0.5, 0.05);

        assertTrue(Double.isFinite(result.score()), "score must not be NaN/Infinite for a fresh profile");
        assertTrue(result.score() >= 5.0 && result.score() <= 99.0, "score must respect its clamp range");
    }

    @Test
    void contributionsCanGoNegativeExpressingRealTensions() {
        // A naive `share * affinity` formulation is non-negative by construction,
        // which makes an honest "tension" impossible to express. Centring on
        // ANCHOR is what allows a real mismatch to show up as a negative number.
        ScoringService svc = newService();
        TraitVector user = TraitVector.of(new double[] {0.95, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5});
        TraitVector movie = TraitVector.of(new double[] {0.05, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5});
        double[] highConfidence = new double[Trait.count()];
        java.util.Arrays.fill(highConfidence, 0.9);

        ScoringService.Scored result = svc.score(
                user, highConfidence, movie, 0.9, "TMDB_KEYWORDS", 0.5, 0.9);

        boolean anyNegative = result.explanation().getAll().stream()
                .anyMatch(c -> c.getContribution() < 0);
        assertTrue(anyNegative, "a strong user/movie mismatch on one axis must be able to show as a negative contribution");
    }

    @Test
    void largestRemainderRoundingSumsExactlyAcrossRandomInputs() {
        // Direct test of the rounding primitive itself, independent of the rest of
        // the scoring pipeline: round each part to one decimal such that they sum
        // exactly to a given target, never approximately.
        Random rnd = new Random(42);
        for (int trial = 0; trial < 500; trial++) {
            List<TraitContribution> parts = new java.util.ArrayList<>();
            double target = 0;
            for (Trait t : Trait.values()) {
                double c = (rnd.nextDouble() - 0.5) * 40;
                target += c;
                parts.add(new TraitContribution(t, 0.5, 0.5, 0.1, 0.5, c, ""));
            }
            double roundedTarget = Math.round(target * 10.0) / 10.0;

            ScoringService.largestRemainderRound(parts, roundedTarget);

            double sum = parts.stream().mapToDouble(TraitContribution::getContribution).sum();
            assertEquals(Math.round(roundedTarget * 10.0), Math.round(sum * 10.0),
                    "rounded parts must sum exactly to the rounded target");
        }
    }
}
