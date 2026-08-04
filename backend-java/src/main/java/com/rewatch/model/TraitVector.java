package com.rewatch.model;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable point in trait space: one value in [0,1] per {@link Trait},
 * indexed by {@code Trait.ordinal()}.
 *
 * Used for both movies ("how much does this story exhibit each trait") and users
 * ("how much do you prefer each trait"), which is what makes them directly
 * comparable — the whole recommendation engine rests on that symmetry.
 *
 * 0.5 is the neutral midpoint and means "no signal", not "medium amount". Movie
 * vectors are deliberately shrunk toward 0.5 when we have little evidence about
 * a title, so an unknown movie ranks mid-pack instead of scoring spuriously.
 */
public final class TraitVector {

    public static final double NEUTRAL = 0.5;

    private final double[] values;

    private TraitVector(double[] values) {
        this.values = values;
    }

    /** All traits at the neutral midpoint — the "we know nothing" vector. */
    public static TraitVector neutral() {
        double[] v = new double[Trait.count()];
        java.util.Arrays.fill(v, NEUTRAL);
        return new TraitVector(v);
    }

    /** Copies and clamps; the caller's array is not retained. */
    public static TraitVector of(double[] raw) {
        if (raw == null || raw.length != Trait.count()) {
            throw new IllegalArgumentException(
                    "trait vector must have exactly " + Trait.count() + " values, got "
                            + (raw == null ? "null" : String.valueOf(raw.length)));
        }
        double[] v = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            v[i] = clamp(raw[i]);
        }
        return new TraitVector(v);
    }

    /** Missing keys fall back to {@link #NEUTRAL}. */
    public static TraitVector fromMap(Map<Trait, Double> map) {
        double[] v = new double[Trait.count()];
        for (Trait t : Trait.values()) {
            Double d = map == null ? null : map.get(t);
            v[t.ordinal()] = d == null ? NEUTRAL : clamp(d);
        }
        return new TraitVector(v);
    }

    public double get(Trait t) {
        return values[t.ordinal()];
    }

    public double get(int ordinal) {
        return values[ordinal];
    }

    /** Defensive copy — TraitVector is immutable. */
    public double[] toArray() {
        return values.clone();
    }

    /** Keyed by wire key, in enum declaration order, for JSON serialisation. */
    public Map<String, Double> toKeyedMap() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Trait t : Trait.values()) {
            out.put(t.key(), values[t.ordinal()]);
        }
        return out;
    }

    public Map<Trait, Double> toEnumMap() {
        Map<Trait, Double> out = new EnumMap<>(Trait.class);
        for (Trait t : Trait.values()) {
            out.put(t, values[t.ordinal()]);
        }
        return out;
    }

    /** Returns a copy with one trait replaced. */
    public TraitVector with(Trait t, double value) {
        double[] v = values.clone();
        v[t.ordinal()] = clamp(value);
        return new TraitVector(v);
    }

    /**
     * Pulls every component a fraction of the way toward {@link #NEUTRAL}.
     * Used to express uncertainty: a title we know little about should not make
     * confident-looking claims about its own traits.
     *
     * @param keep 1.0 keeps the vector as-is, 0.0 collapses it to all-neutral.
     */
    public TraitVector shrinkToNeutral(double keep) {
        double k = clamp(keep);
        double[] v = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            v[i] = NEUTRAL + (values[i] - NEUTRAL) * k;
        }
        return new TraitVector(v);
    }

    /** Component-wise mean. Returns neutral for an empty input. */
    public static TraitVector mean(java.util.List<TraitVector> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return neutral();
        }
        double[] acc = new double[Trait.count()];
        for (TraitVector vec : vectors) {
            for (int i = 0; i < acc.length; i++) {
                acc[i] += vec.values[i];
            }
        }
        for (int i = 0; i < acc.length; i++) {
            acc[i] /= vectors.size();
        }
        return new TraitVector(acc);
    }

    /**
     * Cosine similarity on the neutral-centred vectors, in [-1,1].
     *
     * Centring matters: raw cosine over all-positive vectors is compressed into a
     * narrow ~0.85-1.0 band and cannot distinguish anything. Used for
     * "similar taste" comparisons and diversity re-ranking, NOT for match scores
     * (those need per-trait attribution, see ScoringService).
     */
    public double centredCosine(TraitVector other) {
        double dot = 0, magA = 0, magB = 0;
        for (int i = 0; i < values.length; i++) {
            double a = values[i] - NEUTRAL;
            double b = other.values[i] - NEUTRAL;
            dot += a * b;
            magA += a * a;
            magB += b * b;
        }
        double denom = Math.sqrt(magA) * Math.sqrt(magB);
        if (denom < 1e-9) {
            return 0.0;
        }
        return dot / denom;
    }

    public static double clamp(double v) {
        if (Double.isNaN(v)) {
            return NEUTRAL;
        }
        if (v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TraitVector{");
        for (Trait t : Trait.values()) {
            if (t.ordinal() > 0) {
                sb.append(", ");
            }
            sb.append(t.key()).append('=').append(String.format("%.2f", values[t.ordinal()]));
        }
        return sb.append('}').toString();
    }
}
