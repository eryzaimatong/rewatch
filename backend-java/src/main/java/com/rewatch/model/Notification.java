package com.rewatch.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * A single in-app notification. `readAt` is a timestamp rather than a boolean
 * — consistent with this codebase's preference for timestamp-of-event fields
 * (TraitEvent.occurredAt, WatchlistItem.addedAt) — and gives "mark read at
 * time X" for free.
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notifications_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_notifications_user_read", columnList = "user_id,read_at")
    }
)
public class Notification {

    public enum Type { NEW_FOLLOWER, TASTE_MILESTONE, DNA_MATCH }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Recipient. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, length = 300)
    private String message;

    /** Nullable — the other user this notification is about, for deep-linking. */
    @Column(name = "related_user_id")
    private Long relatedUserId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Notification() {}

    public Notification(Long userId, Type type, String message, Long relatedUserId, Instant createdAt) {
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.relatedUserId = relatedUserId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    public Type getType() { return type; }
    public void setType(Type v) { this.type = v; }

    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }

    public Long getRelatedUserId() { return relatedUserId; }
    public void setRelatedUserId(Long v) { this.relatedUserId = v; }

    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant v) { this.readAt = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
