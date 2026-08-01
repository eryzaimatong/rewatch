package com.rewatch.dto;

import java.util.List;

public class MovieDTO {
    private Long id;
    private String title;
    private String overview;
    private String posterPath;
    private String backdropPath;
    private String releaseDate;
    private Integer year;
    private double matchScore;
    private List<String> reasons;

    public MovieDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public double getMatchScore() { return matchScore; }
    public void setMatchScore(double matchScore) { this.matchScore = matchScore; }

    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }
}