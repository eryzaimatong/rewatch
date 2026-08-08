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
 * A user following someone else's public watchlist folder — the "Collections"
 * layer the critique wanted, built on the WatchlistFolder that already exists
 * rather than a parallel entity. Same directed-edge shape as {@link Follow},
 * just pointed at a folder instead of a user.
 */
@Entity
@Table(
    name = "collection_follows",
    uniqueConstraints = @UniqueConstraint(name = "uk_collection_follow", columnNames = {"user_id", "folder_id"}),
    indexes = {
        @Index(name = "idx_collection_follows_user", columnList = "user_id"),
        @Index(name = "idx_collection_follows_folder", columnList = "folder_id")
    }
)
public class CollectionFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "folder_id", nullable = false)
    private Long folderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public CollectionFollow() {}

    public CollectionFollow(Long userId, Long folderId, Instant createdAt) {
        this.userId = userId;
        this.folderId = folderId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    public Long getFolderId() { return folderId; }
    public void setFolderId(Long v) { this.folderId = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
