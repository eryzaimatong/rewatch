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
 * A one-time password-reset secret, deliberately separate from the JWT stack —
 * this needs to be a single-use, DB-checkable token (so it can be marked
 * consumed and can't be replayed), which a signed-but-stateless JWT can't give
 * us without adding a token-blacklist anyway.
 */
@Entity
@Table(
    name = "password_reset_tokens",
    indexes = {
        @Index(name = "idx_prt_token", columnList = "token"),
        @Index(name = "idx_prt_user", columnList = "user_id")
    }
)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * A SHA-256 hex digest of the raw token, not the raw token itself — see
     * PasswordResetService.hashToken(). The raw value only ever exists in
     * the request that generated it and the email it goes out in; nothing
     * durable stores it, so a leaked read of this table doesn't hand out
     * directly usable reset links.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Null until consumed. Checked so a link can't be replayed after use. */
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PasswordResetToken() {}

    public PasswordResetToken(Long userId, String token, Instant expiresAt, Instant createdAt) {
        this.userId = userId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    public String getToken() { return token; }
    public void setToken(String v) { this.token = v; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant v) { this.expiresAt = v; }

    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant v) { this.usedAt = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }

    public boolean isValid(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
