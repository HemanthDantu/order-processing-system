package com.hemanth.orderprocessingsystem.order;

import com.hemanth.orderprocessingsystem.config.OpenApiConfig;
import com.hemanth.orderprocessingsystem.auth.JwtPrincipal;
import com.hemanth.orderprocessingsystem.idempotency.IdempotencyService;
import com.hemanth.orderprocessingsystem.order.dto.CreateOrderRequest;
import com.hemanth.orderprocessingsystem.order.dto.OrderResponse;
import com.hemanth.orderprocessingsystem.order.dto.OrderSummaryResponse;
import com.hemanth.orderprocessingsystem.order.dto.PageResponse;
import com.hemanth.orderprocessingsystem.order.dto.UpdateOrderStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HTTP endpoints for order operations.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    public OrderController(OrderService orderService, IdempotencyService idempotencyService) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Creates a new order once per authenticated user and Idempotency-Key.
     */
    @Operation(
            summary = "Create order",
            description = "Creates a pending order for the authenticated user. Requires a unique Idempotency-Key header.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Order created"),
                    @ApiResponse(responseCode = "400", description = "Invalid request or missing Idempotency-Key"),
                    @ApiResponse(responseCode = "401", description = "Authentication required"),
                    @ApiResponse(responseCode = "409", description = "Idempotency conflict")
            }
    )
    @PostMapping
    public ResponseEntity<String> createOrder(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Client-generated key used to safely retry order creation", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        return idempotencyService.createOrder(request, principal, idempotencyKey);
    }

    /**
     * Retrieves order details, including line items.
     */
    @Operation(
            summary = "Get order",
            description = "Returns order details. Customers can only access their own orders; admins can access any order.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order found"),
                    @ApiResponse(responseCode = "401", description = "Authentication required"),
                    @ApiResponse(responseCode = "403", description = "Access denied"),
                    @ApiResponse(responseCode = "404", description = "Order not found")
            }
    )
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID orderId
    ) {
        return ResponseEntity.ok(orderService.getOrder(orderId, principal));
    }

    /**
     * Lists orders using bounded pagination and optional status filtering.
     */
    @Operation(
            summary = "List orders",
            description = "Lists orders with optional status filtering. Admins see all orders; customers see only their own orders.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Orders returned"),
                    @ApiResponse(responseCode = "401", description = "Authentication required")
            }
    )
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
    @Operation(
            summary = "Update order status",
            description = "Applies an admin-only order status transition.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Status updated"),
                    @ApiResponse(responseCode = "400", description = "Invalid status transition"),
                    @ApiResponse(responseCode = "401", description = "Authentication required"),
                    @ApiResponse(responseCode = "403", description = "Admin role required"),
                    @ApiResponse(responseCode = "404", description = "Order not found"),
                    @ApiResponse(responseCode = "409", description = "Optimistic locking conflict")
            }
    )
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
    @Operation(
            summary = "Cancel order",
            description = "Cancels a pending order. Customers can cancel their own orders; admins can cancel any pending order.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Order cancelled or already cancelled"),
                    @ApiResponse(responseCode = "400", description = "Order cannot be cancelled from its current state"),
                    @ApiResponse(responseCode = "401", description = "Authentication required"),
                    @ApiResponse(responseCode = "403", description = "Access denied"),
                    @ApiResponse(responseCode = "404", description = "Order not found")
            }
    )
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID orderId
    ) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId, principal));
    }
}
