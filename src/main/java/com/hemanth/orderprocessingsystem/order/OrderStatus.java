package com.hemanth.orderprocessingsystem.order;

/**
 * Supported order lifecycle states.
 */
public enum OrderStatus {
    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
