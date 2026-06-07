package com.hemanth.orderprocessingsystem.processing;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Timer-driven entry point for automatic pending-order processing.
 */
@Component
public class OrderProcessingScheduler {

    private final OrderProcessingJobService jobService;

    public OrderProcessingScheduler(OrderProcessingJobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Runs after the configured delay from the previous run completion.
     */
    @Scheduled(fixedDelayString = "${order.processing.scheduler.fixed-rate-ms}")
    public void processPendingOrders() {
        jobService.processPendingOrders();
    }
}
