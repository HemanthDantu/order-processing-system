package com.hemanth.orderprocessingsystem.order;

import com.hemanth.orderprocessingsystem.auth.JwtPrincipal;
import com.hemanth.orderprocessingsystem.order.dto.CreateOrderRequest;
import com.hemanth.orderprocessingsystem.order.dto.OrderResponse;
import com.hemanth.orderprocessingsystem.order.dto.OrderSummaryResponse;
import com.hemanth.orderprocessingsystem.order.dto.PageResponse;
import com.hemanth.orderprocessingsystem.order.dto.UpdateOrderStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HTTP endpoints for order operations.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates a new order for the authenticated user.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request, principal));
    }

    /**
     * Retrieves order details, including line items.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID orderId
    ) {
        return ResponseEntity.ok(orderService.getOrder(orderId, principal));
    }

    /**
     * Lists orders for admins using bounded pagination and optional status filtering.
     */
    @GetMapping
    public ResponseEntity<PageResponse<OrderSummaryResponse>> listOrders(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status
    ) {
        return ResponseEntity.ok(orderService.listOrders(status, page, size, principal));
    }

    /**
     * Updates an order status through an admin-only lifecycle transition.
     */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return ResponseEntity.ok(orderService.updateStatus(orderId, request, principal));
    }

    /**
     * Cancels an order when the caller owns it or is an admin.
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID orderId
    ) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId, principal));
    }
}
