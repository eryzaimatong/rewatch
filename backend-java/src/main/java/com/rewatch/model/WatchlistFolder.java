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
 * A named shelf a user can file saved titles into — "Rainy Day", "Need to
 * Cry", "Comfort". Items with no folder sit in the default unsorted
 * watchlist (WatchlistItem.folderId == null).
 */
@Entity
@Table(
    name = "watchlist_folders",
    uniqueConstraints = @UniqueConstraint(name = "uk_watchlist_folder_name", columnNames = {"user_id", "name"}),
    indexes = @Index(name = "idx_watchlist_folders_user", columnList = "user_id")
)
public class WatchlistFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Shareable on the owner's public profile (GET /api/social/{userId}/lists).
     * Private by default. `columnDefinition` (not just `nullable = false`) is
     * required here — ddl-auto=update's plain ALTER TABLE ADD COLUMN ... NOT
     * NULL fails outright against the already-populated watchlist_folders table
     * (Postgres has no value to backfill existing rows with); the explicit
     * `default false` in the DDL is what lets the column actually get added.
     */
    @Column(name = "is_public", nullable = false, columnDefinition = "boolean not null default false")
    private boolean isPublic = false;

    /**
     * When true (and only meaningful alongside isPublic — see
     * WatchlistService.setFolderCollaborative), any authenticated user can add
     * titles to this folder, not just its owner. WatchlistItem still records
     * who actually added each title (its own userId), so a collaborative
     * folder is several people's real contributions, not one person's list
     * with borrowed credit.
     */
    @Column(name = "collaborative", nullable = false, columnDefinition = "boolean not null default false")
    private boolean collaborative = false;

    public WatchlistFolder() {}

    public WatchlistFolder(Long userId, String name, Instant createdAt) {
        this.userId = userId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean v) { this.isPublic = v; }

    public boolean isCollaborative() { return collaborative; }
    public void setCollaborative(boolean v) { this.collaborative = v; }
}
