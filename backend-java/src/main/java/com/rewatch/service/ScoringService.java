package com.rewatch.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rewatch.dto.MatchExplanation;
import com.rewatch.dto.TraitContribution;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitVector;

/**
 * Computes a match score AND the per-trait attribution that explains it.
 *
 * Shape of the model (additive, SHAP-style):
 *
 *   salience_i = (2*|u_i - 0.5|)^GAMMA        how opinionated the user is here
 *   w_i        = conf_i * (FLOOR + (1-FLOOR)*salience_i)
 *   s_i        = w_i / sum(w)                 normalised weight share, sums to 1
 *   a_i        = 1 - |u_i - m_i|              affinity on this trait
 *   c_i        = SLOPE * s_i * (a_i - ANCHOR) SIGNED contribution
 *   score      = MID + sum(c_i) + qualityBonus
 *
 * Three details that a naive version gets wrong, each of which breaks the feature:
 *
 * 1. THE WEIGHT FLOOR IS NOT OPTIONAL. Salience is exactly zero for a user whose
 *    profile sits at 0.5 — which is every new user. Without the floor, sum(w) is
 *    zero and every score is NaN for exactly the users you demo with.
 *
 * 2. CONTRIBUTIONS MUST BE ABLE TO GO NEGATIVE. Centring affinity on ANCHOR is
 *    what allows that. A formula of the form s_i * a_i is non-negative by
 *    construction, so "tensions" would be impossible to express honestly — the
 *    best you could do is relabel the smallest positive contribution, which is a
 *    lie dressed up as a number.
 *
 * 3. CALIBRATION IS REQUIRED OR EVERYTHING CLUSTERS. With realistic vectors,
 *    E[a] ~ 0.83 and sd(A) ~ 0.05, so raw affinity spans only about 74-93. SLOPE
 *    stretches that to roughly 5-95. The mapping is ABSOLUTE, never relative to
 *    the current result pool: the same title must not show one percentage in the
 *    feed and a different one in search.
 */
@Service
public class ScoringService {

    private static final double W_FLOOR = 0.15;
    private static final double GAMMA = 1.3;
    private static final double MID = 50.0;
    private static final double SCORE_MIN = 5.0;
    private static final double SCORE_MAX = 99.0;

    /** Strength of the audience-quality prior, faded out as confidence grows. */
    private static final double QUALITY_PRIOR = 12.0;

    private static final double DRIVER_THRESHOLD = 0.8;

    /** Mean affinity for a typical profile against the catalog. Re-fit from /api/admin/feature-stats. */
    @Value("${rewatch.scoring.anchor:0.80}")
    private double anchor;

    /** Points per unit of affinity. ~22 points per standard deviation. */
    @Value("${rewatch.scoring.slope:440.0}")
    private double slope;

    public record Scored(double score, MatchExplanation explanation) {}

    /**
     * @param user       the user's trait preferences
     * @param confidence per-trait confidence, same order as Trait.ordinal()
     * @param movie      the title's derived trait vector
     * @param featureConfidence how much the title's own vector is trusted
     * @param quality    normalised audience score 0..1
     * @param meanConfidence average user confidence; fades the quality prior out
     */
    public Scored score(TraitVector user, double[] confidence, TraitVector movie,
                        double featureConfidence, String featuresSource,
                        double quality, double meanConfidence) {

        int n = Trait.count();
        double[] w = new double[n];
        double totalW = 0.0;

        for (Trait t : Trait.values()) {
            int i = t.ordinal();
            double salienceRaw = Math.min(1.0, 2.0 * Math.abs(user.get(t) - TraitVector.NEUTRAL));
            double salience = Math.pow(salienceRaw, GAMMA);
            w[i] = confidence[i] * (W_FLOOR + (1.0 - W_FLOOR) * salience);
            totalW += w[i];
        }
        totalW = Math.max(totalW, 1e-6);

        double[] share = new double[n];
        double[] affinity = new double[n];
        double weightedAffinity = 0.0;

        for (Trait t : Trait.values()) {
            int i = t.ordinal();
            share[i] = w[i] / totalW;
            affinity[i] = 1.0 - Math.abs(user.get(t) - movie.get(t));
            weightedAffinity += share[i] * affinity[i];
        }

        double qualityBonus = QUALITY_PRIOR * (1.0 - meanConfidence) * (quality - 0.5);

        // Overall-confidence gate. `share[i]` is normalised to sum to 1 regardless of
        // the ABSOLUTE size of the weights behind it — a brand-new user has identical
        // (tiny) confidence on every axis, which is uniform, so weightedAffinity comes
        // out as a plain average of per-trait affinities with nothing damping it. Since
        // movie vectors cluster near the neutral midpoint too (most titles aren't
        // extreme on most axes), a neutral user's affinity to nearly everything is high
        // — every title scored ~97-99% before this gate existed, which is precisely the
        // fabricated-confidence failure mode this whole scoring model exists to avoid.
        // meanConfidence reuses VectorEngine's evidence curve (n/(n+K)), so the gate
        // opens gradually as real ratings accumulate instead of on/off.
        double confidenceGate = meanConfidence;

        double uncapped = MID + confidenceGate * slope * (weightedAffinity - anchor) + qualityBonus;
        double score = Math.max(SCORE_MIN, Math.min(SCORE_MAX, uncapped));

        // The clamp has to be absorbed proportionally, or the parts stop summing to
        // the whole for any title that hits the ceiling or the floor.
        double denom = uncapped - MID;
        double shrink = Math.abs(denom) < 1e-9 ? 1.0 : (score - MID) / denom;

        double shownQualityBonus = qualityBonus * shrink;

        List<TraitContribution> all = new ArrayList<>(n);
        for (Trait t : Trait.values()) {
            int i = t.ordinal();
            double c = confidenceGate * slope * share[i] * (affinity[i] - anchor) * shrink;
            String text = t.blurbFor(c >= 0, movie.get(t) >= 0.5);
            all.add(new TraitContribution(t, user.get(t), movie.get(t),
                    share[i], affinity[i], c, text));
        }

        // Round so the displayed parts sum exactly to the displayed whole.
        double target = round1(score) - MID - round1(shownQualityBonus);
        largestRemainderRound(all, target);

        List<TraitContribution> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingDouble(TraitContribution::getContribution).reversed());

        List<TraitContribution> drivers = sorted.stream()
                .filter(c -> c.getContribution() > DRIVER_THRESHOLD)
                .limit(3)
                .toList();

        List<TraitContribution> tensions = sorted.stream()
                .filter(c -> c.getContribution() < -DRIVER_THRESHOLD)
                .sorted(Comparator.comparingDouble(TraitContribution::getContribution))
                .limit(2)
                .toList();

        MatchExplanation explanation = new MatchExplanation(
                MID, round1(shownQualityBonus), drivers, tensions, all,
                summarise(drivers, tensions), featureConfidence, featuresSource);

        return new Scored(round1(score), explanation);
    }

    private String summarise(List<TraitContribution> drivers, List<TraitContribution> tensions) {
        if (drivers.isEmpty() && tensions.isEmpty()) {
            return "Balanced fit across your profile.";
        }
        StringBuilder sb = new StringBuilder();
        if (!drivers.isEmpty()) {
            sb.append("Matches you on ").append(drivers.get(0).getLabel().toLowerCase());
            if (drivers.size() > 1) {
                sb.append(" and ").append(drivers.get(1).getLabel().toLowerCase());
            }
            sb.append('.');
        }
        if (!tensions.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("Pushes against your ")
              .append(tensions.get(0).getLabel().toLowerCase())
              .append('.');
        }
        return sb.toString();
    }

    /**
     * Rounds each contribution to one decimal such that they sum EXACTLY to
     * {@code target}.
     *
     * Rounding each value independently would leave the printed numbers visibly
     * failing to add up to the printed score, which destroys the credibility of the
     * whole explanation on first inspection. Largest-remainder assigns the leftover
     * tenths to the values that were rounded down the most.
     */
    static void largestRemainderRound(List<TraitContribution> parts, double target) {
        int n = parts.size();
        if (n == 0) {
            return;
        }

        long[] floors = new long[n];
        double[] remainders = new double[n];
        long sumFloors = 0;

        for (int i = 0; i < n; i++) {
            double scaled = parts.get(i).getContribution() * 10.0;
            long f = (long) Math.floor(scaled);
            floors[i] = f;
            remainders[i] = scaled - f;   // always in [0,1), including for negatives
            sumFloors += f;
        }

        long targetScaled = Math.round(target * 10.0);
        long toDistribute = targetScaled - sumFloors;
        toDistribute = Math.max(0, Math.min(n, toDistribute));

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        final double[] rem = remainders;
        java.util.Arrays.sort(order, (a, b) -> Double.compare(rem[b], rem[a]));

        for (int k = 0; k < toDistribute; k++) {
            floors[order[k]] += 1;
        }

        for (int i = 0; i < n; i++) {
            parts.get(i).setContribution(floors[i] / 10.0);
        }
    }

    static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    public double getAnchor() { return anchor; }
    public double getSlope() { return slope; }
}
