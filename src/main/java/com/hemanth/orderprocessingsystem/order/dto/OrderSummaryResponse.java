package com.hemanth.orderprocessingsystem.order.dto;

import com.hemanth.orderprocessingsystem.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight order representation for paginated lists.
 *
 * @param id order id
 * @param customerId owner of the order
 * @param status current lifecycle status
 * @param currency ISO-4217 currency code
 * @param totalAmount full order total
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 */
public record OrderSummaryResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        String currency,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt
) {
}
