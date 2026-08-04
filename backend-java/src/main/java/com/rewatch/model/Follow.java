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

/**
 * A directed follow edge. followerId is always the authenticated caller —
 * SocialController never accepts it from a request body/param, so a user can
 * only ever create edges where they themselves are the follower.
 */
@Entity
@Table(
    name = "follows",
    uniqueConstraints = @UniqueConstraint(name = "uk_follow_edge", columnNames = {"follower_id", "followee_id"}),
    indexes = {
        @Index(name = "idx_follows_follower", columnList = "follower_id"),
        @Index(name = "idx_follows_followee", columnList = "followee_id")
    }
)
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follower_id", nullable = false)
    private Long followerId;

    @Column(name = "followee_id", nullable = false)
    private Long followeeId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Follow() {}

    public Follow(Long followerId, Long followeeId, Instant createdAt) {
        this.followerId = followerId;
        this.followeeId = followeeId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFollowerId() { return followerId; }
    public void setFollowerId(Long v) { this.followerId = v; }

    public Long getFolloweeId() { return followeeId; }
    public void setFolloweeId(Long v) { this.followeeId = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
