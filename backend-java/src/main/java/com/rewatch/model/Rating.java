package com.rewatch.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * A user's multi-facet rating of one title. Append-only: this is the source of
 * truth from which the entire taste profile is replayed.
 *
 * There is deliberately NO unique constraint on (userId, titleId). Re-rating a
 * title is additional evidence, not a correction — which also means replay never
 * has to reverse a clamped EMA step.
 *
 * Facets are 1..5. Only `overall` is required; the rest may be null when a user
 * rates quickly.
 */
@Entity
@Table(
    name = "ratings",
    indexes = {
        @Index(name = "idx_ratings_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_ratings_user_title", columnList = "user_id,title_id")
    }
)
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title_id", nullable = false)
    private Long titleId;

    private Integer overall;
    private Integer chars;
    private Integer ending;
    private Integer visuals;
    private Integer story;
    private Integer rewatch;

    @Column(length = 500)
    private String moment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Rating() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getTitleId() { return titleId; }
    public void setTitleId(Long titleId) { this.titleId = titleId; }

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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
