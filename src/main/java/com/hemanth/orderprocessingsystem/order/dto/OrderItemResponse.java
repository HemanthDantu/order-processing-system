package com.hemanth.orderprocessingsystem.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for an order line item.
 *
 * @param id line item id
 * @param productId product identifier captured at order time
 * @param productName product name captured at order time
 * @param quantity number of units ordered
 * @param unitPrice price per unit
 * @param lineTotal calculated total for the line
 */
public record OrderItemResponse(
        UUID id,
        String productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
