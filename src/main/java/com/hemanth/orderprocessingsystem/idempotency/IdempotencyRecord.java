package com.hemanth.orderprocessingsystem.idempotency;

import com.hemanth.orderprocessingsystem.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores the server-side result of an idempotent order creation request.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 255)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyStatus status;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected IdempotencyRecord() {
        // JPA requires a no-args constructor for entity hydration.
    }

    /**
     * Creates a new IN_PROGRESS idempotency record before work begins.
     */
    public IdempotencyRecord(UUID id, User user, String idempotencyKey, String requestHash, Instant createdAt) {
        this.id = id;
        this.user = user;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = IdempotencyStatus.IN_PROGRESS;
        this.createdAt = createdAt;
    }

    /**
     * Stores the successful HTTP response for future retries.
     */
    public void markCompleted(String responseBody, int statusCode, UUID resourceId, Instant completedAt) {
        this.responseBody = responseBody;
        this.statusCode = statusCode;
        this.resourceId = resourceId;
        this.completedAt = completedAt;
        this.status = IdempotencyStatus.COMPLETED;
    }

    /**
     * Marks the request as failed so clients do not wait forever on retries.
     */
    public void markFailed(Instant completedAt) {
        this.completedAt = completedAt;
        this.status = IdempotencyStatus.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
