package com.rewatch.model;

import java.time.Instant;

/**
 * The state of one trait at a point in time: how strong it is, how much evidence
 * stands behind it, and which way it is moving.
 *
 * The wire field names (val/conf/trend/evid/updated) are the frontend's existing
 * contract and are kept as-is. `updated` is now a real {@link Instant} — it used
 * to be the string literal "2026-08-02" at every construction site, which made
 * any time-series view impossible.
 */
public class TraitNode {

    private double val;
    private double conf;
    private int trend;
    private int evid;
    private Instant updated;

    public TraitNode() {}

    public TraitNode(double val, double conf, int trend, int evid, Instant updated) {
        this.val = val;
        this.conf = conf;
        this.trend = trend;
        this.evid = evid;
        this.updated = updated;
    }

    /** A trait we know nothing about yet: neutral value, near-zero confidence. */
    public static TraitNode unknown(Instant at) {
        return new TraitNode(TraitVector.NEUTRAL, 0.05, 0, 0, at);
    }

    public double getVal() { return val; }
    public void setVal(double val) { this.val = val; }

    public double getConf() { return conf; }
    public void setConf(double conf) { this.conf = conf; }

    public int getTrend() { return trend; }
    public void setTrend(int trend) { this.trend = trend; }

    public int getEvid() { return evid; }
    public void setEvid(int evid) { this.evid = evid; }

    public Instant getUpdated() { return updated; }
    public void setUpdated(Instant updated) { this.updated = updated; }
}
