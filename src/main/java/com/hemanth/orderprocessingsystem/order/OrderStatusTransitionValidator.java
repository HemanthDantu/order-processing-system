package com.hemanth.orderprocessingsystem.order;

import com.hemanth.orderprocessingsystem.exception.InvalidOrderStateException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralizes valid order lifecycle transitions.
 *
 * <p>Controllers and services should call this class instead of duplicating
 * status transition rules in request handlers.</p>
 */
@Component
public class OrderStatusTransitionValidator {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED));
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    /**
     * Validates that a status transition is allowed.
     *
     * @param currentStatus current persisted order status
     * @param targetStatus requested next order status
     * @throws InvalidOrderStateException when the transition is not allowed
     */
    public void validate(OrderStatus currentStatus, OrderStatus targetStatus) {
        if (!isAllowed(currentStatus, targetStatus)) {
            throw new InvalidOrderStateException(currentStatus, targetStatus);
        }
    }

    /**
     * Checks whether a transition is valid without throwing.
     */
    public boolean isAllowed(OrderStatus currentStatus, OrderStatus targetStatus) {
        return ALLOWED_TRANSITIONS
                .getOrDefault(currentStatus, Set.of())
                .contains(targetStatus);
    }
}
