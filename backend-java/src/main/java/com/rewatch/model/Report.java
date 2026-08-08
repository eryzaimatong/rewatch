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

/** A user-filed report against another user. No admin frontend yet — reviewed via GET /api/admin/reports. */
@Entity
@Table(
    name = "reports",
    indexes = {
        @Index(name = "idx_reports_status_created", columnList = "status,created_at"),
        @Index(name = "idx_reports_reporter", columnList = "reporter_id"),
        @Index(name = "idx_reports_reported", columnList = "reported_user_id")
    }
)
public class Report {

    public enum Reason { SPAM, HARASSMENT, INAPPROPRIATE_CONTENT, IMPERSONATION, OTHER }

    public enum Status { OPEN, REVIEWED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "reported_user_id", nullable = false)
    private Long reportedUserId;

    /**
     * Optional — set when this report was filed against a specific
     * ReviewComment rather than a user's profile/activity in general, so an
     * admin reviewing it can see the exact content instead of just "this
     * user, for some reason." Null for the original profile-level report
     * flow, which predates comments existing at all.
     */
    @Column(name = "comment_id")
    private Long commentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Reason reason;

    @Column(length = 500)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Report() {}

    public Report(Long reporterId, Long reportedUserId, Reason reason, String details, Instant createdAt) {
        this.reporterId = reporterId;
        this.reportedUserId = reportedUserId;
        this.reason = reason;
        this.details = details;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long v) { this.reporterId = v; }

    public Long getReportedUserId() { return reportedUserId; }
    public void setReportedUserId(Long v) { this.reportedUserId = v; }

    public Long getCommentId() { return commentId; }
    public void setCommentId(Long v) { this.commentId = v; }

    public Reason getReason() { return reason; }
    public void setReason(Reason v) { this.reason = v; }

    public String getDetails() { return details; }
    public void setDetails(String v) { this.details = v; }

    public Status getStatus() { return status; }
    public void setStatus(Status v) { this.status = v; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
