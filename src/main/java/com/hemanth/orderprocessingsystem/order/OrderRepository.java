package com.hemanth.orderprocessingsystem.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for order aggregate persistence.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {
}
