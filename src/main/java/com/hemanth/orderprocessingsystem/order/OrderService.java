package com.hemanth.orderprocessingsystem.order;

import com.hemanth.orderprocessingsystem.auth.JwtPrincipal;
import com.hemanth.orderprocessingsystem.history.ActorType;
import com.hemanth.orderprocessingsystem.history.OrderStatusHistoryService;
import com.hemanth.orderprocessingsystem.order.dto.CreateOrderItemRequest;
import com.hemanth.orderprocessingsystem.order.dto.CreateOrderRequest;
import com.hemanth.orderprocessingsystem.order.dto.OrderResponse;
import com.hemanth.orderprocessingsystem.order.dto.OrderSummaryResponse;
import com.hemanth.orderprocessingsystem.order.dto.PageResponse;
import com.hemanth.orderprocessingsystem.order.dto.UpdateOrderStatusRequest;
import com.hemanth.orderprocessingsystem.user.User;
import com.hemanth.orderprocessingsystem.user.UserRepository;
import com.hemanth.orderprocessingsystem.user.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Application service for order use cases.
 */
@Service
public class OrderService {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderStatusHistoryService historyService;
    private final OrderMapper orderMapper;
    private final OrderStatusTransitionValidator transitionValidator;

    public OrderService(
            OrderRepository orderRepository,
            UserRepository userRepository,
            OrderStatusHistoryService historyService,
            OrderMapper orderMapper,
            OrderStatusTransitionValidator transitionValidator
    ) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.historyService = historyService;
        this.orderMapper = orderMapper;
        this.transitionValidator = transitionValidator;
    }

    /**
     * Creates a PENDING order for the authenticated user.
     *
     * <p>The customer id is taken from the verified JWT principal, never from
     * the request body. This keeps customers from creating orders on behalf of
     * another user.</p>
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, JwtPrincipal principal) {
        User customer = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));

        Instant now = Instant.now();
        Order order = new Order(
                UUID.randomUUID(),
                customer,
                OrderStatus.PENDING,
                MoneyUtil.DEFAULT_CURRENCY,
                BigDecimal.ZERO.setScale(2),
                now,
                now
        );

        BigDecimal totalAmount = BigDecimal.ZERO.setScale(2);
        for (CreateOrderItemRequest itemRequest : request.items()) {
            BigDecimal unitPrice = MoneyUtil.normalize(itemRequest.unitPrice());
            BigDecimal lineTotal = MoneyUtil.lineTotal(itemRequest.quantity(), unitPrice);
            OrderItem item = new OrderItem(
                    UUID.randomUUID(),
                    itemRequest.productId(),
                    itemRequest.productName(),
                    itemRequest.quantity(),
                    unitPrice,
                    lineTotal
            );
            order.addItem(item);
            totalAmount = totalAmount.add(lineTotal);
        }

        order.setTotalAmount(MoneyUtil.normalize(totalAmount));
        Order savedOrder = orderRepository.save(order);
        historyService.recordOrderCreated(savedOrder, customer, ActorType.valueOf(principal.role().name()), now);
        return orderMapper.toResponse(savedOrder);
    }

    /**
     * Retrieves an order detail response with customer ownership enforcement.
     *
     * <p>Admins can view any order. Customers can only view orders where the
     * persisted {@code customer_id} matches their JWT principal.</p>
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, JwtPrincipal principal) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (principal.role() != UserRole.ADMIN && !order.getCustomer().getId().equals(principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this order");
        }

        return orderMapper.toResponse(order);
    }

    /**
     * Lists orders for admins with optional status filtering and bounded pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listOrders(
            OrderStatus status,
            int page,
            int size,
            JwtPrincipal principal
    ) {
        if (principal.role() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can list orders");
        }

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<Order> orders = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);

        return new PageResponse<>(
                orders.getContent().stream().map(orderMapper::toSummaryResponse).toList(),
                orders.getNumber(),
                orders.getSize(),
                orders.getTotalElements(),
                orders.getTotalPages(),
                orders.isFirst(),
                orders.isLast()
        );
    }

    /**
     * Applies an admin-requested status transition and writes an audit row.
     *
     * <p>The lifecycle rule check stays in {@link OrderStatusTransitionValidator}
     * so status rules remain centralized and easy to test.</p>
     */
    @Transactional
    public OrderResponse updateStatus(UUID orderId, UpdateOrderStatusRequest request, JwtPrincipal principal) {
        if (principal.role() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can update order status");
        }

        User admin = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        OrderStatus previousStatus = order.getStatus();
        transitionValidator.validate(previousStatus, request.status());

        Instant now = Instant.now();
        order.setStatus(request.status());
        order.setUpdatedAt(now);

        Order savedOrder = orderRepository.save(order);
        historyService.recordStatusChange(
                savedOrder,
                previousStatus,
                request.status(),
                admin,
                ActorType.ADMIN,
                now,
                request.reason()
        );

        return orderMapper.toResponse(savedOrder);
    }
}
