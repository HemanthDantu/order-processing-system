package com.hemanth.orderprocessingsystem.history;

import com.hemanth.orderprocessingsystem.order.Order;
import com.hemanth.orderprocessingsystem.order.OrderStatus;
import com.hemanth.orderprocessingsystem.user.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes audit records for order status changes.
 */
@Service
public class OrderStatusHistoryService {

    private final OrderStatusHistoryRepository repository;

    public OrderStatusHistoryService(OrderStatusHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Records the initial PENDING status when an order is created.
     */
    public void recordOrderCreated(Order order, User actor, ActorType actorType, Instant changedAt) {
        OrderStatusHistory history = new OrderStatusHistory(
                UUID.randomUUID(),
                order,
                null,
                OrderStatus.PENDING,
                actor,
                actorType,
                changedAt,
                "Order created"
        );
        repository.save(history);
    }
}
