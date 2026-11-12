package com.rewatch.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.rewatch.model.Trait;
import com.rewatch.model.TraitVector;

/**
 * The pure parts of Movie Night: the five mood questions, how answers become a
 * trait vector, and how a group's individual scores become one fair score.
 * No I/O, so it is covered by plain unit tests.
 *
 * Group score = mean - SPREAD_PENALTY * stddev. A title three friends rate
 * 80/80/80 beats one they rate 100/100/40 even though the means are close,
 * because the point of a shared night is that nobody is stuck watching
 * something they'd hate.
 */
public final class NightMath {

    private NightMath() { }

    public static final double SPREAD_PENALTY = 0.75;
    public static final int MAX_MEMBERS = 8;

    public record Option(String label, Map<Trait, Double> deltas) { }

    public record Question(String id, String prompt, List<Option> options) { }

    private static Option opt(String label, Object... pairs) {
        Map<Trait, Double> m = new EnumMap<>(Trait.class);
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((Trait) pairs[i], (Double) pairs[i + 1]);
        }
        return new Option(label, m);
    }

    public static final List<Question> QUESTIONS = List.of(
        new Question("energy", "What's your energy tonight?", List.of(
            opt("Slow and cozy", Trait.PACING, -0.25, Trait.COMFORT, 0.25, Trait.INTENSITY, -0.2),
            opt("Somewhere in the middle"),
            opt("Edge of my seat", Trait.PACING, 0.25, Trait.INTENSITY, 0.3))),
        new Question("feeling", "What do you want to feel?", List.of(
            opt("Laugh until it hurts", Trait.HUMOR, 0.35, Trait.HOPE, 0.1),
            opt("Swoon a little", Trait.ROMANCE, 0.35, Trait.HOPE, 0.1),
            opt("Cry a little", Trait.BITTER, 0.3, Trait.NOSTALGIA, 0.2),
            opt("Think about it for days", Trait.GROWTH, 0.35, Trait.BITTER, 0.1))),
        new Question("people", "Who should the story be about?", List.of(
            opt("A big found family", Trait.FAMILY, 0.35, Trait.COMFORT, 0.1),
            opt("One person's journey", Trait.FAMILY, -0.3, Trait.GROWTH, 0.2))),
        new Question("ending", "How should it end?", List.of(
            opt("Happy, please", Trait.HOPE, 0.3, Trait.BITTER, -0.3),
            opt("Bittersweet", Trait.BITTER, 0.3, Trait.NOSTALGIA, 0.1),
            opt("Surprise me"))),
        new Question("fresh", "Comfort or discovery?", List.of(
            opt("Something that feels like home", Trait.NOSTALGIA, 0.3, Trait.COMFORT, 0.25),
            opt("Something I've never seen", Trait.GROWTH, 0.2, Trait.NOSTALGIA, -0.25))));

    /** Returns null when answers are not exactly one valid option index per question. */
    public static TraitVector vectorFromAnswers(List<Integer> answers) {
        if (answers == null || answers.size() != QUESTIONS.size()) {
            return null;
        }
        double[] v = new double[Trait.count()];
        java.util.Arrays.fill(v, TraitVector.NEUTRAL);
        for (int q = 0; q < QUESTIONS.size(); q++) {
            Integer a = answers.get(q);
            List<Option> options = QUESTIONS.get(q).options();
            if (a == null || a < 0 || a >= options.size()) {
                return null;
            }
            for (Map.Entry<Trait, Double> e : options.get(a).deltas().entrySet()) {
                v[e.getKey().ordinal()] += e.getValue();
            }
        }
        return TraitVector.of(v); // clamps to [0,1]
    }

    public static double mean(List<Double> xs) {
        return xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    public static double stddev(List<Double> xs) {
        if (xs.size() < 2) {
            return 0;
        }
        double m = mean(xs);
        double var = xs.stream().mapToDouble(x -> (x - m) * (x - m)).sum() / xs.size();
        return Math.sqrt(var);
    }

    public static double groupScore(List<Double> memberScores) {
        return Math.max(0, mean(memberScores) - SPREAD_PENALTY * stddev(memberScores));
    }

    /** Member-wise average of the vectors — the group's shared taste. */
    public static TraitVector average(List<TraitVector> vectors) {
        double[] v = new double[Trait.count()];
        for (TraitVector tv : vectors) {
            for (int i = 0; i < v.length; i++) {
                v[i] += tv.get(i);
            }
        }
        for (int i = 0; i < v.length; i++) {
            v[i] /= Math.max(1, vectors.size());
        }
        return TraitVector.of(v);
    }

    /**
     * The trait where the title and the group agree most *on something the
     * group actually cares about* (far from neutral) — the "why this one" line.
     */
    public static Trait sharedReason(TraitVector group, TraitVector title) {
        Trait best = null;
        double bestScore = -1;
        for (Trait t : Trait.values()) {
            double care = Math.abs(group.get(t) - TraitVector.NEUTRAL);
            double agreement = 1 - Math.abs(group.get(t) - title.get(t));
            double s = care * agreement;
            if (s > bestScore) {
                bestScore = s;
                best = t;
            }
        }
        return best;
    }

    public static String encodeVector(TraitVector v) {
        List<String> parts = new ArrayList<>();
        for (double d : v.toArray()) {
            parts.add(String.format(java.util.Locale.ROOT, "%.4f", d));
        }
        return String.join(",", parts);
    }

    public static TraitVector decodeVector(String csv) {
        String[] p = csv.split(",");
        double[] v = new double[p.length];
        for (int i = 0; i < p.length; i++) {
            v[i] = Double.parseDouble(p[i]);
        }
        return TraitVector.of(v);
    }

    /** Trims, drops control characters and angle brackets, caps length. Null if nothing usable is left. */
    public static String sanitizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replaceAll("[\\p{Cntrl}<>]", "").replaceAll("\\s+", " ").trim();
        if (s.length() > 24) {
            s = s.substring(0, 24).trim();
        }
        return s.isEmpty() ? null : s;
    }
}
