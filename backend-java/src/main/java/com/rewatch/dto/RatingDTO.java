package com.rewatch.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A rating submission. `tmdbId` is required because MovieModal/RateModal send
 * `movie.id`, which for anything sourced from a TMDB list is the TMDB id, not a
 * local titles.id — the controller resolves-or-creates the local Title before
 * writing the Rating row.
 */
public class RatingDTO {

    @NotNull
    private Long userId;

    /** TMDB id of the title being rated. */
    private Integer tmdbId;

    /** Local titles.id, used when the caller already knows it. One of the two is required. */
    private Long titleId;

    private String title;

    @NotNull @Min(1) @Max(5)
    private Integer overall;

    @Min(1) @Max(5) private Integer chars;
    @Min(1) @Max(5) private Integer ending;
    @Min(1) @Max(5) private Integer visuals;
    @Min(1) @Max(5) private Integer story;
    @Min(1) @Max(5) private Integer rewatch;

    private String moment;

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    public Integer getTmdbId() { return tmdbId; }
    public void setTmdbId(Integer v) { this.tmdbId = v; }

    public Long getTitleId() { return titleId; }
    public void setTitleId(Long v) { this.titleId = v; }

    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }

    public Integer getOverall() { return overall; }
    public void setOverall(Integer v) { this.overall = v; }

    public Integer getChars() { return chars; }
    public void setChars(Integer v) { this.chars = v; }

    public Integer getEnding() { return ending; }
    public void setEnding(Integer v) { this.ending = v; }

    public Integer getVisuals() { return visuals; }
    public void setVisuals(Integer v) { this.visuals = v; }

    public Integer getStory() { return story; }
    public void setStory(Integer v) { this.story = v; }

    public Integer getRewatch() { return rewatch; }
    public void setRewatch(Integer v) { this.rewatch = v; }

    public String getMoment() { return moment; }
    public void setMoment(String v) { this.moment = v; }
}
