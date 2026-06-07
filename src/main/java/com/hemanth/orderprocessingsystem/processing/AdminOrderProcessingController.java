package com.hemanth.orderprocessingsystem.processing;

import com.hemanth.orderprocessingsystem.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
    @Operation(
            summary = "Trigger order processing job",
            description = "Runs the pending-order processing job immediately. Admin-only endpoint.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Processing job completed"),
                    @ApiResponse(responseCode = "401", description = "Authentication required"),
                    @ApiResponse(responseCode = "403", description = "Admin role required")
            }
    )
    @PostMapping("/trigger")
    public ResponseEntity<OrderProcessingJobResponse> triggerOrderProcessing() {
        return ResponseEntity.ok(jobService.processPendingOrders());
    }
}
