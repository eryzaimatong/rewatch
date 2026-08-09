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
 * Current watch state for one user+title — "Currently Watching" or "Dropped".
 * Unlike {@link Rating}, which is deliberately append-only evidence, this is a
 * mutable current-state row (one per user+title, overwritten in place): a
 * title can't be both "still watching" and "dropped" at once, so there's
 * nothing to replay or reverse here. "Watched" and "Plan to watch" are
 * deliberately NOT stored here — they're already derivable from having a
 * Rating row, and from being in a watchlist folder, respectively.
 */
@Entity
@Table(
    name = "watch_statuses",
    uniqueConstraints = @UniqueConstraint(name = "uk_watch_status_user_title", columnNames = {"user_id", "title_id"}),
    indexes = @Index(name = "idx_watch_status_user", columnList = "user_id")
)
public class WatchStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title_id", nullable = false)
    private Long titleId;

    /** "WATCHING" or "DROPPED" — validated in WatchStatusService, not enforced here. */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WatchStatus() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    public Long getTitleId() { return titleId; }
    public void setTitleId(Long v) { this.titleId = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
