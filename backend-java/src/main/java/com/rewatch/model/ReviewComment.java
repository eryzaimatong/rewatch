package com.rewatch.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** A reply to someone's review (a Rating with a non-blank `moment`). Flat, no threading. */
@Entity
@Table(
    name = "review_comments",
    indexes = {
        @Index(name = "idx_review_comments_rating", columnList = "rating_id,created_at"),
        @Index(name = "idx_review_comments_author", columnList = "author_user_id")
    }
)
public class ReviewComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rating_id", nullable = false)
    private Long ratingId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Self-reported by the commenter — hides the body behind a reveal until clicked. */
    @Column(name = "has_spoilers", columnDefinition = "boolean not null default false")
    private boolean hasSpoilers;

    public ReviewComment() {}

    public ReviewComment(Long ratingId, Long authorUserId, String body, Instant createdAt) {
        this.ratingId = ratingId;
        this.authorUserId = authorUserId;
        this.body = body;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRatingId() { return ratingId; }
    public void setRatingId(Long v) { this.ratingId = v; }

    public Long getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(Long v) { this.authorUserId = v; }

    public String getBody() { return body; }
    public void setBody(String v) { this.body = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }

    public boolean isHasSpoilers() { return hasSpoilers; }
    public void setHasSpoilers(boolean v) { this.hasSpoilers = v; }
}
