package com.hemanth.orderprocessingsystem.processing;

import java.time.Instant;

/**
 * Summary of one scheduled order-processing job execution.
 *
 * @param processedCount number of orders moved from PENDING to PROCESSING
 * @param durationMs elapsed job runtime in milliseconds
 * @param startedAt timestamp when the job started
 * @param completedAt timestamp when the job completed
 */
public record OrderProcessingJobResponse(
        int processedCount,
        long durationMs,
        Instant startedAt,
        Instant completedAt
) {
}
