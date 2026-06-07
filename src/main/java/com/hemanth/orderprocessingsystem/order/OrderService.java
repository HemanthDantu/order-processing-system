package com.hemanth.orderprocessingsystem.order;

import com.hemanth.orderprocessingsystem.auth.JwtPrincipal;
import com.hemanth.orderprocessingsystem.history.ActorType;
import com.hemanth.orderprocessingsystem.history.OrderStatusHistoryService;
import com.hemanth.orderprocessingsystem.order.dto.CreateOrderItemRequest;
import com.hemanth.orderprocessingsystem.order.dto.CreateOrderRequest;
import com.hemanth.orderprocessingsystem.order.dto.OrderResponse;
import com.hemanth.orderprocessingsystem.user.User;
import com.hemanth.orderprocessingsystem.user.UserRepository;
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

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderStatusHistoryService historyService;
    private final OrderMapper orderMapper;

    public OrderService(
            OrderRepository orderRepository,
            UserRepository userRepository,
            OrderStatusHistoryService historyService,
            OrderMapper orderMapper
    ) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.historyService = historyService;
        this.orderMapper = orderMapper;
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
}
