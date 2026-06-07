package com.hemanth.orderprocessingsystem.order;

import com.hemanth.orderprocessingsystem.auth.JwtPrincipal;
import com.hemanth.orderprocessingsystem.history.ActorType;
import com.hemanth.orderprocessingsystem.history.OrderStatusHistoryService;
import com.hemanth.orderprocessingsystem.order.dto.CreateOrderItemRequest;
import com.hemanth.orderprocessingsystem.order.dto.CreateOrderRequest;
import com.hemanth.orderprocessingsystem.order.dto.OrderResponse;
import com.hemanth.orderprocessingsystem.order.dto.OrderSummaryResponse;
import com.hemanth.orderprocessingsystem.order.dto.PageResponse;
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

    @Test
    void createOrderCalculatesTotalsAndWritesHistory() {
        OrderService service = new OrderService(orderRepository, userRepository, historyService, orderMapper);
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
        OrderService service = new OrderService(orderRepository, userRepository, historyService, orderMapper);
        Order order = sampleOrder(customerUser());
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        OrderResponse response = service.getOrder(ORDER_ID, customerPrincipal());

        assertThat(response.id()).isEqualTo(ORDER_ID);
        assertThat(response.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void getOrderRejectsCustomerAccessToAnotherCustomersOrder() {
        OrderService service = new OrderService(orderRepository, userRepository, historyService, orderMapper);
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
        OrderService service = new OrderService(orderRepository, userRepository, historyService, orderMapper);
        Order order = sampleOrder(customerUser());
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        OrderResponse response = service.getOrder(ORDER_ID, adminPrincipal());

        assertThat(response.id()).isEqualTo(ORDER_ID);
    }

    @Test
    void listOrdersRejectsCustomer() {
        OrderService service = new OrderService(orderRepository, userRepository, historyService, orderMapper);

        assertThatThrownBy(() -> service.listOrders(null, 0, 20, customerPrincipal()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listOrdersReturnsCleanPaginatedAdminResponse() {
        OrderService service = new OrderService(orderRepository, userRepository, historyService, orderMapper);
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
        OrderService service = new OrderService(orderRepository, userRepository, historyService, orderMapper);
        Order order = sampleOrder(customerUser());
        when(orderRepository.findByStatus(eq(OrderStatus.PENDING), eq(PageRequest.of(0, 100))))
                .thenReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 100), 1));

        PageResponse<OrderSummaryResponse> response = service.listOrders(OrderStatus.PENDING, 0, 250, adminPrincipal());

        assertThat(response.content()).hasSize(1);
        assertThat(response.size()).isEqualTo(100);
        verify(orderRepository).findByStatus(OrderStatus.PENDING, PageRequest.of(0, 100));
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
}
