package com.hemanth.orderprocessingsystem.order;

import com.hemanth.orderprocessingsystem.exception.InvalidOrderStateException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for centralized order lifecycle transition rules.
 */
class OrderStatusTransitionValidatorTest {

    private final OrderStatusTransitionValidator validator = new OrderStatusTransitionValidator();

    @Test
    void allowsConfiguredTransitions() {
        assertThatCode(() -> validator.validate(OrderStatus.PENDING, OrderStatus.PROCESSING)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(OrderStatus.PENDING, OrderStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(OrderStatus.PROCESSING, OrderStatus.SHIPPED)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(OrderStatus.SHIPPED, OrderStatus.DELIVERED)).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidTransitions() {
        assertThatThrownBy(() -> validator.validate(OrderStatus.PENDING, OrderStatus.SHIPPED))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessage("Invalid order status transition from PENDING to SHIPPED");

        assertThatThrownBy(() -> validator.validate(OrderStatus.PROCESSING, OrderStatus.CANCELLED))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThatThrownBy(() -> validator.validate(OrderStatus.DELIVERED, OrderStatus.PROCESSING))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThatThrownBy(() -> validator.validate(OrderStatus.CANCELLED, OrderStatus.PENDING))
                .isInstanceOf(InvalidOrderStateException.class);
    }
}
