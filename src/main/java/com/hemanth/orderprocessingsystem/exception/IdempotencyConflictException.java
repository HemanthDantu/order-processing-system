package com.hemanth.orderprocessingsystem.exception;

/**
 * Raised when an idempotency key cannot be reused safely.
 */
public class IdempotencyConflictException extends RuntimeException {

    private final Integer retryAfterSeconds;

    public IdempotencyConflictException(String message) {
        this(message, null);
    }

    public IdempotencyConflictException(String message, Integer retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Optional Retry-After value for concurrent in-progress requests.
     */
    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
