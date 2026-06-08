package com.hemanth.orderprocessingsystem.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runtime configuration for the scheduled pending-order processor.
 *
 * <p>The property still uses the assignment's original {@code fixed-rate-ms}
 * name. The scheduler interprets the value as a fixed delay so a long-running
 * batch completes before the next delay period begins.</p>
 */
@Component
@ConfigurationProperties(prefix = "order.processing.scheduler")
public class OrderProcessingSchedulerProperties {

    /**
     * Delay between the completion of one scheduler run and the start of the next.
     */
    private long fixedRateMs = 300_000;

    /**
     * Maximum number of pending orders to claim in one run. Batching keeps each
     * transaction bounded even if the queue grows.
     */
    private int batchSize = 500;

    public long getFixedRateMs() {
        return fixedRateMs;
    }

    public void setFixedRateMs(long fixedRateMs) {
        this.fixedRateMs = fixedRateMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
