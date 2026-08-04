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
import jakarta.persistence.UniqueConstraint;

/**
 * One trait of one user's current taste profile — the materialised result of
 * replaying that user's rating log.
 *
 * Row-per-trait rather than column-per-trait so that adding an eleventh trait is
 * a data change, not a schema migration.
 *
 * STRING enum mapping is mandatory. With ORDINAL, reordering the Trait enum would
 * silently rewrite the meaning of every historical row.
 */
@Entity
@Table(
    name = "user_traits",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_traits", columnNames = {"user_id", "trait"}),
    indexes = @Index(name = "idx_user_traits_user", columnList = "user_id")
)
public class UserTrait {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Trait trait;

    /** Preference strength, 0..1. */
    @Column(nullable = false)
    private double value;

    /** How much evidence stands behind {@link #value}, 0..1. */
    @Column(nullable = false)
    private double confidence;

    /** -1 falling, 0 stable, +1 rising. */
    @Column(nullable = false)
    private int trend;

    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserTrait() {}

    public UserTrait(Long userId, Trait trait, TraitNode node) {
        this.userId = userId;
        this.trait = trait;
        applyNode(node);
    }

    public void applyNode(TraitNode node) {
        this.value = node.getVal();
        this.confidence = node.getConf();
        this.trend = node.getTrend();
        this.evidenceCount = node.getEvid();
        this.updatedAt = node.getUpdated();
    }

    public TraitNode toNode() {
        return new TraitNode(value, confidence, trend, evidenceCount, updatedAt);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    public Trait getTrait() { return trait; }
    public void setTrait(Trait v) { this.trait = v; }

    public double getValue() { return value; }
    public void setValue(double v) { this.value = v; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double v) { this.confidence = v; }

    public int getTrend() { return trend; }
    public void setTrend(int v) { this.trend = v; }

    public int getEvidenceCount() { return evidenceCount; }
    public void setEvidenceCount(int v) { this.evidenceCount = v; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
