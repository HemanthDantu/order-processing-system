package com.hemanth.orderprocessingsystem.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Repository for order aggregate persistence.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Returns orders filtered by status for admin list views.
     */
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
}
