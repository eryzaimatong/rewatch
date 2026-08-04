package com.rewatch.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

/**
 * Shape matches what Onboarding.jsx already sends: favourite titles, a genre
 * love/neutral/avoid map, dealbreaker tags, and pacing/intensity sliders.
 */
public class OnboardingRequest {

    @NotNull
    private Long userId;

    private List<String> favs;

    /** genre name -> "love" | "neutral" | "avoid" */
    private Map<String, String> genres;

    private List<String> avoid;

    private Integer pacing;
    private Integer intensity;
    private String runtime;

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    public List<String> getFavs() { return favs; }
    public void setFavs(List<String> v) { this.favs = v; }

    public Map<String, String> getGenres() { return genres; }
    public void setGenres(Map<String, String> v) { this.genres = v; }

    public List<String> getAvoid() { return avoid; }
    public void setAvoid(List<String> v) { this.avoid = v; }

    public Integer getPacing() { return pacing; }
    public void setPacing(Integer v) { this.pacing = v; }

    public Integer getIntensity() { return intensity; }
    public void setIntensity(Integer v) { this.intensity = v; }

    public String getRuntime() { return runtime; }
    public void setRuntime(String v) { this.runtime = v; }
}
