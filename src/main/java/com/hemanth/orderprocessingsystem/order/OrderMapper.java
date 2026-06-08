package com.hemanth.orderprocessingsystem.order;

import com.hemanth.orderprocessingsystem.order.dto.OrderItemResponse;
import com.hemanth.orderprocessingsystem.order.dto.OrderResponse;
import com.hemanth.orderprocessingsystem.order.dto.OrderSummaryResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts order entities into API response DTOs.
 */
@Component
public class OrderMapper {

    /**
     * Builds the detailed order response used by create/get/update/cancel endpoints.
     *
     * <p>Call this from inside service transactions because the order's item and
     * customer associations are intentionally lazy to avoid accidental eager query
     * fan-out in list views.</p>
     */
    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomer().getId(),
                order.getStatus(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items
        );
    }

    /**
     * Builds the lightweight list response without exposing JPA entities.
     */
    public OrderSummaryResponse toSummaryResponse(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getCustomer().getId(),
                order.getStatus(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal()
        );
    }
}
