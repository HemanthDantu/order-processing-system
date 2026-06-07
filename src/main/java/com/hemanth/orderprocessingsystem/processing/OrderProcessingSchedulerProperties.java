package com.hemanth.orderprocessingsystem.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runtime configuration for the scheduled pending-order processor.
 */
@Component
@ConfigurationProperties(prefix = "order.processing.scheduler")
public class OrderProcessingSchedulerProperties {

    /**
     * How often the scheduler should run, in milliseconds.
     */
    private long fixedRateMs = 300_000;

    /**
     * Maximum number of pending orders to claim in one run.
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
