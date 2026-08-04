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
 * One saved title. Replaces the old behaviour where "saving" a title only
 * flipped local React state (Dashboard.jsx used to fake the whole watchlist
 * from the first 4 catalog titles) — this is a real, persisted save.
 *
 * `folderId` is nullable: null means the item sits in the default unsorted
 * watchlist rather than a named shelf.
 */
@Entity
@Table(
    name = "watchlist_items",
    uniqueConstraints = @UniqueConstraint(name = "uk_watchlist_item", columnNames = {"user_id", "title_id"}),
    indexes = {
        @Index(name = "idx_watchlist_items_user", columnList = "user_id"),
        @Index(name = "idx_watchlist_items_folder", columnList = "folder_id")
    }
)
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title_id", nullable = false)
    private Long titleId;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    public WatchlistItem() {}

    public WatchlistItem(Long userId, Long titleId, Long folderId, Instant addedAt) {
        this.userId = userId;
        this.titleId = titleId;
        this.folderId = folderId;
        this.addedAt = addedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    public Long getTitleId() { return titleId; }
    public void setTitleId(Long v) { this.titleId = v; }

    public Long getFolderId() { return folderId; }
    public void setFolderId(Long v) { this.folderId = v; }

    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant v) { this.addedAt = v; }
}
