package com.rewatch.features;

import com.rewatch.model.Trait;

/**
 * A signed nudge applied to trait space by one piece of evidence (a genre, a
 * keyword, a phrase). Deltas accumulate additively in unbounded "raw" space and
 * are squashed to 0..1 exactly once, at the end, by FeatureDeriver.
 *
 * Negative components matter as much as positive ones: without them every movie
 * accumulates only upward and the whole catalog clusters high, which is precisely
 * what made the old index-derived scores indistinguishable.
 */
public final class TraitDelta {

    private final double[] d;

    private TraitDelta(double[] d) {
        this.d = d;
    }

    public static Builder of() {
        return new Builder();
    }

    public double get(int ordinal) {
        return d[ordinal];
    }

    public double[] raw() {
        return d;
    }

    public static final class Builder {
        private final double[] d = new double[Trait.count()];

        public Builder family(double v)    { d[Trait.FAMILY.ordinal()] = v;    return this; }
        public Builder nostalgia(double v) { d[Trait.NOSTALGIA.ordinal()] = v; return this; }
        public Builder growth(double v)    { d[Trait.GROWTH.ordinal()] = v;    return this; }
        public Builder pacing(double v)    { d[Trait.PACING.ordinal()] = v;    return this; }
        public Builder humor(double v)     { d[Trait.HUMOR.ordinal()] = v;     return this; }
        public Builder romance(double v)   { d[Trait.ROMANCE.ordinal()] = v;   return this; }
        public Builder intensity(double v) { d[Trait.INTENSITY.ordinal()] = v; return this; }
        public Builder hope(double v)      { d[Trait.HOPE.ordinal()] = v;      return this; }
        public Builder bitter(double v)    { d[Trait.BITTER.ordinal()] = v;    return this; }
        public Builder comfort(double v)   { d[Trait.COMFORT.ordinal()] = v;   return this; }

        public TraitDelta build() {
            return new TraitDelta(d);
        }
    }
}
