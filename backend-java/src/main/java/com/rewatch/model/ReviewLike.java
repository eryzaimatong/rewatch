package com.rewatch.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A user liking someone's review (a Rating with a non-blank `moment`). */
@Entity
@Table(
    name = "review_likes",
    uniqueConstraints = @UniqueConstraint(name = "uk_review_like", columnNames = {"user_id", "rating_id"}),
    indexes = {
        @Index(name = "idx_review_likes_user", columnList = "user_id"),
        @Index(name = "idx_review_likes_rating", columnList = "rating_id")
    }
)
public class ReviewLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "rating_id", nullable = false)
    private Long ratingId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ReviewLike() {}

    public ReviewLike(Long userId, Long ratingId, Instant createdAt) {
        this.userId = userId;
        this.ratingId = ratingId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    public Long getRatingId() { return ratingId; }
    public void setRatingId(Long v) { this.ratingId = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
