package com.hemanth.orderprocessingsystem.order.dto;

import com.hemanth.orderprocessingsystem.order.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for an admin-driven order status change.
 *
 * @param status target order status
 * @param reason optional audit reason for the status change
 */
public record UpdateOrderStatusRequest(
        @NotNull(message = "Status is required")
        OrderStatus status,
        @Size(max = 500, message = "Reason cannot exceed 500 characters")
        String reason
) {
}
