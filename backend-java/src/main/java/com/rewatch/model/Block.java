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
 * A directed block edge, mirroring Follow.java's shape exactly. Visibility
 * checks (SocialService.isBlocked) treat this as mutual — A blocking B hides
 * both directions — but the row itself only ever records who did the
 * blocking, so unblock can only be done by the blocker, not forced by the
 * blocked party.
 */
@Entity
@Table(
    name = "blocks",
    uniqueConstraints = @UniqueConstraint(name = "uk_block_edge", columnNames = {"blocker_id", "blocked_id"}),
    indexes = {
        @Index(name = "idx_blocks_blocker", columnList = "blocker_id"),
        @Index(name = "idx_blocks_blocked", columnList = "blocked_id")
    }
)
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_id", nullable = false)
    private Long blockerId;

    @Column(name = "blocked_id", nullable = false)
    private Long blockedId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Block() {}

    public Block(Long blockerId, Long blockedId, Instant createdAt) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBlockerId() { return blockerId; }
    public void setBlockerId(Long v) { this.blockerId = v; }

    public Long getBlockedId() { return blockedId; }
    public void setBlockedId(Long v) { this.blockedId = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
