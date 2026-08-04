package com.rewatch.dto;

import java.util.List;

/**
 * Why a title scored what it scored.
 *
 * The invariant the UI depends on:
 *
 *     baseline + sum(all[].contribution) + qualityBonus == matchScore
 *
 * and it holds after rounding, not just in exact arithmetic.
 */
public class MatchExplanation {

    private double baseline;
    private double qualityBonus;
    private List<TraitContribution> drivers;
    private List<TraitContribution> tensions;
    private List<TraitContribution> all;
    private String summary;
    private double featureConfidence;
    private String featuresSource;

    public MatchExplanation() {}

    public MatchExplanation(double baseline, double qualityBonus,
                            List<TraitContribution> drivers,
                            List<TraitContribution> tensions,
                            List<TraitContribution> all,
                            String summary,
                            double featureConfidence,
                            String featuresSource) {
        this.baseline = baseline;
        this.qualityBonus = qualityBonus;
        this.drivers = drivers;
        this.tensions = tensions;
        this.all = all;
        this.summary = summary;
        this.featureConfidence = featureConfidence;
        this.featuresSource = featuresSource;
    }

    public double getBaseline() { return baseline; }
    public double getQualityBonus() { return qualityBonus; }
    public List<TraitContribution> getDrivers() { return drivers; }
    public List<TraitContribution> getTensions() { return tensions; }
    public List<TraitContribution> getAll() { return all; }
    public String getSummary() { return summary; }
    public double getFeatureConfidence() { return featureConfidence; }
    public String getFeaturesSource() { return featuresSource; }
}
