package com.hemanth.orderprocessingsystem.order.dto;

import com.hemanth.orderprocessingsystem.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detailed order response used by create and retrieve operations.
 *
 * @param id order id
 * @param customerId owner of the order
 * @param status current lifecycle status
 * @param currency ISO-4217 currency code
 * @param totalAmount full order total
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 * @param items full line item details
 */
public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        String currency,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items
) {
}
