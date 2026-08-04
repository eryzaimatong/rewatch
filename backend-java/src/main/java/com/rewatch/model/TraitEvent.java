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
 * Append-only log of every change to a user's trait profile. This is what makes
 * "your taste evolved" a real, queryable claim rather than a decorative label.
 *
 * Chosen over periodic snapshots because of {@link #sourceRatingId}: it lets the
 * timeline attribute a movement to its cause — "your Bittersweet rose 0.04 after
 * you rated Parasite 5 stars" — which a daily rollup would discard. At ten rows
 * per rating the volume is trivial, and bucketing is done at query time so the
 * chart granularity can change without a migration.
 */
@Entity
@Table(
    name = "trait_events",
    indexes = {
        @Index(name = "idx_trait_events_user_time", columnList = "user_id,occurred_at"),
        @Index(name = "idx_trait_events_user_trait_time", columnList = "user_id,trait,occurred_at")
    }
)
public class TraitEvent {

    public enum Source { ONBOARDING, RATING, RECOMPUTE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Trait trait;

    @Column(name = "value_before", nullable = false)
    private double valueBefore;

    @Column(name = "value_after", nullable = false)
    private double valueAfter;

    @Column(nullable = false)
    private double delta;

    @Column(name = "confidence_after", nullable = false)
    private double confidenceAfter;

    @Column(name = "evidence_after", nullable = false)
    private int evidenceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private Source sourceType;

    /** Null for onboarding events. Joins back to name the causing title. */
    @Column(name = "source_rating_id")
    private Long sourceRatingId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public TraitEvent() {}

    public TraitEvent(Long userId, Trait trait, TraitNode before, TraitNode after,
                      Source sourceType, Long sourceRatingId, Instant occurredAt) {
        this.userId = userId;
        this.trait = trait;
        this.valueBefore = before.getVal();
        this.valueAfter = after.getVal();
        this.delta = after.getVal() - before.getVal();
        this.confidenceAfter = after.getConf();
        this.evidenceAfter = after.getEvid();
        this.sourceType = sourceType;
        this.sourceRatingId = sourceRatingId;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Trait getTrait() { return trait; }
    public double getValueBefore() { return valueBefore; }
    public double getValueAfter() { return valueAfter; }
    public double getDelta() { return delta; }
    public double getConfidenceAfter() { return confidenceAfter; }
    public int getEvidenceAfter() { return evidenceAfter; }
    public Source getSourceType() { return sourceType; }
    public Long getSourceRatingId() { return sourceRatingId; }
    public Instant getOccurredAt() { return occurredAt; }
}
