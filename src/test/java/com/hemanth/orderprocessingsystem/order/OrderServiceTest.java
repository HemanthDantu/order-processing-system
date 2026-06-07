package com.hemanth.orderprocessingsystem.order;

import com.hemanth.orderprocessingsystem.auth.JwtPrincipal;
import com.hemanth.orderprocessingsystem.history.ActorType;
import com.hemanth.orderprocessingsystem.history.OrderStatusHistoryService;
import com.hemanth.orderprocessingsystem.order.dto.CreateOrderItemRequest;
import com.hemanth.orderprocessingsystem.order.dto.CreateOrderRequest;
import com.hemanth.orderprocessingsystem.order.dto.OrderResponse;
import com.hemanth.orderprocessingsystem.user.User;
import com.hemanth.orderprocessingsystem.user.UserRepository;
import com.hemanth.orderprocessingsystem.user.UserRole;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for order creation business behavior.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

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
}
