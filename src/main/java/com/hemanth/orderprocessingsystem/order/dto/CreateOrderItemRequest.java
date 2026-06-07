package com.hemanth.orderprocessingsystem.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request body for a single order line item.
 *
 * @param productId client-provided product identifier
 * @param productName display name captured at order time
 * @param quantity number of units ordered
 * @param unitPrice price per unit
 */
public record CreateOrderItemRequest(
        @NotBlank(message = "Product id is required")
        String productId,
        @NotBlank(message = "Product name is required")
        String productName,
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        Integer quantity,
        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.00", message = "Unit price must be zero or positive")
        BigDecimal unitPrice
) {
}
