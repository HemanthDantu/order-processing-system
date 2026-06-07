package com.hemanth.orderprocessingsystem.processing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoints for operational order-processing actions.
 */
@RestController
@RequestMapping("/api/v1/admin/scheduler/order-processing")
public class AdminOrderProcessingController {

    private final OrderProcessingJobService jobService;

    public AdminOrderProcessingController(OrderProcessingJobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Runs the same pending-order processing job used by the scheduler.
     */
    @PostMapping("/trigger")
    public ResponseEntity<OrderProcessingJobResponse> triggerOrderProcessing() {
        return ResponseEntity.ok(jobService.processPendingOrders());
    }
}
