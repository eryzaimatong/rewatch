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
 * The actual fix for the email bug surviving unnoticed as long as it did:
 * nothing asserted delivery. "The user shouldn't see a send failure" was
 * never a reason for no one to see it — every send attempt gets a row here,
 * regardless of outcome, so a broken mail path shows up as a query away
 * (SELECT * FROM email_delivery_records WHERE status = 'FAILED') instead of
 * requiring someone to manually trigger a request and watch the logs, which
 * is the only way this session's SMTP-block finding ever surfaced at all.
 */
@Entity
@Table(
    name = "email_delivery_records",
    indexes = {
        @Index(name = "idx_email_delivery_status", columnList = "status"),
        @Index(name = "idx_email_delivery_correlation_id", columnList = "correlation_id")
    }
)
public class EmailDeliveryRecord {

    public enum Type { PASSWORD_RESET, NEW_FOLLOWER }
    public enum Status { PENDING, SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(nullable = false)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /** The sending provider's own message id (e.g. Resend's), once known — null until SENT. */
    @Column(name = "provider_message_id")
    private String providerMessageId;

    /** The exception's message only, not a full stack trace — that goes to the server log, keyed by correlationId. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public EmailDeliveryRecord() {}

    public EmailDeliveryRecord(String correlationId, String recipient, Type type) {
        this.correlationId = correlationId;
        this.recipient = recipient;
        this.type = type;
        this.status = Status.PENDING;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markSent(String providerMessageId) {
        this.status = Status.SENT;
        this.providerMessageId = providerMessageId;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = Status.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCorrelationId() { return correlationId; }
    public String getRecipient() { return recipient; }
    public Type getType() { return type; }
    public Status getStatus() { return status; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
