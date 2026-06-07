package com.hemanth.orderprocessingsystem.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Moves a bounded batch of pending orders into processing.
 */
@Service
public class OrderProcessingJobService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingJobService.class);

    private final OrderProcessingRepository repository;
    private final OrderProcessingSchedulerProperties properties;

    public OrderProcessingJobService(
            OrderProcessingRepository repository,
            OrderProcessingSchedulerProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Runs one processing batch and returns a summary suitable for a future
     * manual trigger endpoint.
     */
    @Transactional
    public OrderProcessingJobResponse processPendingOrders() {
        Instant startedAt = Instant.now();
        int batchSize = Math.max(1, properties.getBatchSize());
        log.info("Scheduled order processing job started with batchSize={}", batchSize);

        try {
            List<UUID> orderIds = repository.claimPendingOrderIds(batchSize);
            Instant changedAt = Instant.now();
            List<UUID> processedOrderIds = repository.markProcessing(orderIds, changedAt);
            repository.insertScheduledProcessingHistory(processedOrderIds, changedAt);

            Instant completedAt = Instant.now();
            long durationMs = Duration.between(startedAt, completedAt).toMillis();
            log.info(
                    "Scheduled order processing job completed processedCount={} durationMs={}",
                    processedOrderIds.size(),
                    durationMs
            );
            return new OrderProcessingJobResponse(processedOrderIds.size(), durationMs, startedAt, completedAt);
        } catch (RuntimeException exception) {
            Instant failedAt = Instant.now();
            long durationMs = Duration.between(startedAt, failedAt).toMillis();
            log.error("Scheduled order processing job failed durationMs={}", durationMs, exception);
            throw exception;
        }
    }
}
