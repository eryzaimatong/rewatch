package com.rewatch.dto;

import com.rewatch.model.Trait;

/**
 * One trait's signed contribution to a match score.
 *
 * {@code contribution} is additive and exact: the contributions of all traits,
 * plus the baseline and the quality bonus, sum to the displayed score. That is the
 * property that makes the explanation trustworthy rather than decorative — a user
 * (or an interviewer) can add the numbers up and they reconcile.
 */
public class TraitContribution {

    private String trait;
    private String label;
    private double userVal;
    private double movieVal;
    private double weightShare;
    private double affinity;
    private double contribution;
    private String text;

    public TraitContribution() {}

    public TraitContribution(Trait trait, double userVal, double movieVal,
                             double weightShare, double affinity,
                             double contribution, String text) {
        this.trait = trait.key();
        this.label = trait.label();
        this.userVal = userVal;
        this.movieVal = movieVal;
        this.weightShare = weightShare;
        this.affinity = affinity;
        this.contribution = contribution;
        this.text = text;
    }

    public String getTrait() { return trait; }
    public String getLabel() { return label; }
    public double getUserVal() { return userVal; }
    public double getMovieVal() { return movieVal; }
    public double getWeightShare() { return weightShare; }
    public double getAffinity() { return affinity; }
    public double getContribution() { return contribution; }
    public void setContribution(double v) { this.contribution = v; }
    public String getText() { return text; }
}
