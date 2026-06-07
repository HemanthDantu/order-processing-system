package com.hemanth.orderprocessingsystem.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for creating a new order.
 *
 * @param items order line items supplied by the customer
 */
public record CreateOrderRequest(
        @NotEmpty(message = "Order must contain at least one item")
        @Size(max = 100, message = "Order cannot contain more than 100 items")
        List<@Valid CreateOrderItemRequest> items
) {
}
