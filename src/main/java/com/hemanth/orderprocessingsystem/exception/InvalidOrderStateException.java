package com.hemanth.orderprocessingsystem.exception;

import com.hemanth.orderprocessingsystem.order.OrderStatus;

/**
 * Raised when an order status transition violates lifecycle rules.
 */
public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(OrderStatus currentStatus, OrderStatus targetStatus) {
        super("Invalid order status transition from " + currentStatus + " to " + targetStatus);
    }
}
