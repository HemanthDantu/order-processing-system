package com.hemanth.orderprocessingsystem.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for order status audit records.
 */
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
}
