package com.hemanth.orderprocessingsystem.idempotency;

/**
 * Persistence state for an idempotent request.
 */
public enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
