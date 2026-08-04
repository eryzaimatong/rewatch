package com.rewatch.dto;

import java.util.List;
import java.util.Map;

public class MovieDTO {

    private Long id;
    private Long titleId;
    private Integer tmdbId;
    private String title;
    private String overview;
    private String posterPath;
    private String backdropPath;
    private String releaseDate;
    private Integer year;
    private String type;
    private double matchScore;

    /**
     * Flat human-readable reasons. Kept as a plain string list because the existing
     * feed UI renders it directly; {@link #explanation} is purely additive.
     */
    private List<String> reasons;

    private MatchExplanation explanation;

    /** The title's own position in trait space, for the radar overlay. */
    private Map<String, Double> storyVector;

    private Double featureConfidence;
    private String featuresSource;
    private Double voteAverage;
    private boolean rated;

    public MovieDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTitleId() { return titleId; }
    public void setTitleId(Long titleId) { this.titleId = titleId; }

    public Integer getTmdbId() { return tmdbId; }
    public void setTmdbId(Integer tmdbId) { this.tmdbId = tmdbId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getOverview() { return overview; }
    public void setOverview(String overview) { this.overview = overview; }

    public String getPosterPath() { return posterPath; }
    public void setPosterPath(String posterPath) { this.posterPath = posterPath; }

    public String getBackdropPath() { return backdropPath; }
    public void setBackdropPath(String backdropPath) { this.backdropPath = backdropPath; }

    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
        if (releaseDate != null && releaseDate.length() >= 4) {
            String y = releaseDate.substring(0, 4);
            if (y.matches("\\d{4}")) {
                this.year = Integer.parseInt(y);
            }
        }
    }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getMatchScore() { return matchScore; }
    public void setMatchScore(double matchScore) { this.matchScore = matchScore; }

    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }

    public MatchExplanation getExplanation() { return explanation; }
    public void setExplanation(MatchExplanation explanation) { this.explanation = explanation; }

    public Map<String, Double> getStoryVector() { return storyVector; }
    public void setStoryVector(Map<String, Double> storyVector) { this.storyVector = storyVector; }

    public Double getFeatureConfidence() { return featureConfidence; }
    public void setFeatureConfidence(Double v) { this.featureConfidence = v; }

    public String getFeaturesSource() { return featuresSource; }
    public void setFeaturesSource(String v) { this.featuresSource = v; }

    public Double getVoteAverage() { return voteAverage; }
    public void setVoteAverage(Double v) { this.voteAverage = v; }

    public boolean isRated() { return rated; }
    public void setRated(boolean rated) { this.rated = rated; }
}
