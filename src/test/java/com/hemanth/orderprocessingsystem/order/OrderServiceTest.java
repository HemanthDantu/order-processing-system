package com.hemanth.orderprocessingsystem.order;

import com.hemanth.orderprocessingsystem.auth.JwtPrincipal;
import com.hemanth.orderprocessingsystem.exception.InvalidOrderStateException;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Unit tests for order creation business behavior.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_CUSTOMER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID ADMIN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderStatusHistoryService historyService;

    private final OrderMapper orderMapper = new OrderMapper();
    private final OrderStatusTransitionValidator transitionValidator = new OrderStatusTransitionValidator();

    @Test
    void createOrderCalculatesTotalsAndWritesHistory() {
        OrderService service = newOrderService();
        User customer = customerUser();
        JwtPrincipal principal = new JwtPrincipal(CUSTOMER_ID, "customer1", UserRole.CUSTOMER);
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new CreateOrderItemRequest("prod-1", "Keyboard", 2, new BigDecimal("49.995")),
                new CreateOrderItemRequest("prod-2", "Mouse", 1, new BigDecimal("25.00"))
        ));

        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = service.createOrder(request, principal);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.currency()).isEqualTo(MoneyUtil.DEFAULT_CURRENCY);
        assertThat(response.totalAmount()).isEqualByComparingTo("125.00");
        assertThat(response.items()).hasSize(2);
        assertThat(savedOrder.getItems()).hasSize(2);
        assertThat(savedOrder.getItems().get(0).getLineTotal()).isEqualByComparingTo("100.00");
        assertThat(savedOrder.getItems().get(1).getLineTotal()).isEqualByComparingTo("25.00");
        verify(historyService).recordOrderCreated(savedOrder, customer, ActorType.CUSTOMER, savedOrder.getCreatedAt());
    }

    @Test
    void getOrderAllowsCustomerToRetrieveOwnOrder() {
        OrderService service = newOrderService();
        Order order = sampleOrder(customerUser());
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        OrderResponse response = service.getOrder(ORDER_ID, customerPrincipal());

        assertThat(response.id()).isEqualTo(ORDER_ID);
        assertThat(response.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void getOrderRejectsCustomerAccessToAnotherCustomersOrder() {
        OrderService service = newOrderService();
        Order order = sampleOrder(new User(
                OTHER_CUSTOMER_ID,
                "other-customer",
                "password-hash",
                UserRole.CUSTOMER,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        ));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.getOrder(ORDER_ID, customerPrincipal()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getOrderAllowsAdminToRetrieveAnyOrder() {
        OrderService service = newOrderService();
        Order order = sampleOrder(customerUser());
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        OrderResponse response = service.getOrder(ORDER_ID, adminPrincipal());

        assertThat(response.id()).isEqualTo(ORDER_ID);
    }

    @Test
    void listOrdersReturnsOnlyCustomerOwnedOrders() {
        OrderService service = newOrderService();
        Order order = sampleOrder(customerUser());
        when(orderRepository.findByCustomerId(CUSTOMER_ID, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        PageResponse<OrderSummaryResponse> response = service.listOrders(null, 0, 20, customerPrincipal());

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).customerId()).isEqualTo(CUSTOMER_ID);
        verify(orderRepository).findByCustomerId(CUSTOMER_ID, PageRequest.of(0, 20));
    }

    @Test
    void listOrdersReturnsCleanPaginatedAdminResponse() {
        OrderService service = newOrderService();
        Order order = sampleOrder(customerUser());
        when(orderRepository.findAll(PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        PageResponse<OrderSummaryResponse> response = service.listOrders(null, 0, 20, adminPrincipal());

        assertThat(response.content()).hasSize(1);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.first()).isTrue();
        assertThat(response.last()).isTrue();
    }

    @Test
    void listOrdersSupportsStatusFilterAndCapsPageSize() {
        OrderService service = newOrderService();
        Order order = sampleOrder(customerUser());
        when(orderRepository.findByStatus(eq(OrderStatus.PENDING), eq(PageRequest.of(0, 100))))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 100), 1));

        PageResponse<OrderSummaryResponse> response = service.listOrders(OrderStatus.PENDING, 0, 250, adminPrincipal());

        assertThat(response.content()).hasSize(1);
        assertThat(response.size()).isEqualTo(100);
        verify(orderRepository).findByStatus(OrderStatus.PENDING, PageRequest.of(0, 100));
    }

    @Test
    void listOrdersSupportsCustomerStatusFilter() {
        OrderService service = newOrderService();
        Order order = sampleOrder(customerUser());
        when(orderRepository.findByCustomerIdAndStatus(eq(CUSTOMER_ID), eq(OrderStatus.PENDING), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1));

        PageResponse<OrderSummaryResponse> response = service.listOrders(OrderStatus.PENDING, 0, 20, customerPrincipal());

        assertThat(response.content()).hasSize(1);
        verify(orderRepository).findByCustomerIdAndStatus(CUSTOMER_ID, OrderStatus.PENDING, PageRequest.of(0, 20));
    }

    @Test
    void updateStatusAllowsAdminValidTransitionAndWritesHistory() {
        OrderService service = newOrderService();
        User admin = adminUser();
        Order order = sampleOrder(customerUser());
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = service.updateStatus(
                ORDER_ID,
                new UpdateOrderStatusRequest(OrderStatus.PROCESSING, "Start processing"),
                adminPrincipal()
        );

        assertThat(response.status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(order.getUpdatedAt()).isAfter(Instant.parse("2026-01-01T00:05:00Z"));
        verify(historyService).recordStatusChange(
                order,
                OrderStatus.PENDING,
                OrderStatus.PROCESSING,
                admin,
                ActorType.ADMIN,
                order.getUpdatedAt(),
                "Start processing"
        );
    }

    @Test
    void updateStatusRejectsInvalidTransition() {
        OrderService service = newOrderService();
        User admin = adminUser();
        Order order = sampleOrder(customerUser());
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateStatus(
                ORDER_ID,
                new UpdateOrderStatusRequest(OrderStatus.SHIPPED, "Skip processing"),
                adminPrincipal()
        )).isInstanceOf(com.hemanth.orderprocessingsystem.exception.InvalidOrderStateException.class);
    }

    @Test
    void updateStatusRejectsCustomer() {
        OrderService service = newOrderService();

        assertThatThrownBy(() -> service.updateStatus(
                ORDER_ID,
                new UpdateOrderStatusRequest(OrderStatus.PROCESSING, "Start processing"),
                customerPrincipal()
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cancelOrderAllowsCustomerToCancelOwnPendingOrderAndWritesHistory() {
        OrderService service = newOrderService();
        User customer = customerUser();
        Order order = sampleOrder(customer);
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = service.cancelOrder(ORDER_ID, customerPrincipal());

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getUpdatedAt()).isAfter(Instant.parse("2026-01-01T00:05:00Z"));
        verify(historyService).recordStatusChange(
                order,
                OrderStatus.PENDING,
                OrderStatus.CANCELLED,
                customer,
                ActorType.CUSTOMER,
                order.getUpdatedAt(),
                "Order cancelled"
        );
    }

    @Test
    void cancelOrderRejectsProcessingOrder() {
        OrderService service = newOrderService();
        User customer = customerUser();
        Order order = sampleOrderWithStatus(customer, OrderStatus.PROCESSING);
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(ORDER_ID, customerPrincipal()))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void cancelOrderReturnsAlreadyCancelledOrderIdempotently() {
        OrderService service = newOrderService();
        User customer = customerUser();
        Order order = sampleOrderWithStatus(customer, OrderStatus.CANCELLED);
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        OrderResponse response = service.cancelOrder(ORDER_ID, customerPrincipal());

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository, never()).save(any(Order.class));
        verify(historyService, never()).recordStatusChange(
                any(Order.class),
                any(OrderStatus.class),
                any(OrderStatus.class),
                any(User.class),
                any(ActorType.class),
                any(Instant.class),
                any(String.class)
        );
    }

    @Test
    void cancelOrderRejectsCustomerCancellingAnotherCustomersOrder() {
        OrderService service = newOrderService();
        User customer = customerUser();
        User otherCustomer = new User(
                OTHER_CUSTOMER_ID,
                "other-customer",
                "password-hash",
                UserRole.CUSTOMER,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
        Order order = sampleOrder(otherCustomer);
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancelOrder(ORDER_ID, customerPrincipal()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cancelOrderAllowsAdminToCancelAnyPendingOrder() {
        OrderService service = newOrderService();
        User admin = adminUser();
        Order order = sampleOrder(customerUser());
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = service.cancelOrder(ORDER_ID, adminPrincipal());

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(historyService).recordStatusChange(
                order,
                OrderStatus.PENDING,
                OrderStatus.CANCELLED,
                admin,
                ActorType.ADMIN,
                order.getUpdatedAt(),
                "Order cancelled"
        );
    }

    private User customerUser() {
        return new User(
                CUSTOMER_ID,
                "customer1",
                "password-hash",
                UserRole.CUSTOMER,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private JwtPrincipal customerPrincipal() {
        return new JwtPrincipal(CUSTOMER_ID, "customer1", UserRole.CUSTOMER);
    }

    private JwtPrincipal adminPrincipal() {
        return new JwtPrincipal(ADMIN_ID, "admin1", UserRole.ADMIN);
    }

    private User adminUser() {
        return new User(
                ADMIN_ID,
                "admin1",
                "password-hash",
                UserRole.ADMIN,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private OrderService newOrderService() {
        return new OrderService(orderRepository, userRepository, historyService, orderMapper, transitionValidator);
    }

    private Order sampleOrder(User customer) {
        Order order = new Order(
                ORDER_ID,
                customer,
                OrderStatus.PENDING,
                MoneyUtil.DEFAULT_CURRENCY,
                new BigDecimal("25.00"),
                Instant.parse("2026-01-01T00:05:00Z"),
                Instant.parse("2026-01-01T00:05:00Z")
        );
        order.addItem(new OrderItem(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "prod-1",
                "Keyboard",
                1,
                new BigDecimal("25.00"),
                new BigDecimal("25.00")
        ));
        return order;
    }

    private Order sampleOrderWithStatus(User customer, OrderStatus status) {
        Order order = sampleOrder(customer);
        order.setStatus(status);
        return order;
    }
}
