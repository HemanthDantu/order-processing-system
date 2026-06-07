package com.hemanth.orderprocessingsystem.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hemanth.orderprocessingsystem.auth.JwtPrincipal;
import com.hemanth.orderprocessingsystem.exception.IdempotencyConflictException;
import com.hemanth.orderprocessingsystem.order.OrderService;
import com.hemanth.orderprocessingsystem.order.OrderStatus;
import com.hemanth.orderprocessingsystem.order.dto.CreateOrderItemRequest;
import com.hemanth.orderprocessingsystem.order.dto.CreateOrderRequest;
import com.hemanth.orderprocessingsystem.order.dto.OrderResponse;
import com.hemanth.orderprocessingsystem.user.User;
import com.hemanth.orderprocessingsystem.user.UserRepository;
import com.hemanth.orderprocessingsystem.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for idempotent order creation behavior.
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private IdempotencyRepository idempotencyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(
                idempotencyRepository,
                userRepository,
                orderService,
                objectMapper,
                new NoOpTransactionManager()
        );
    }

    @Test
    void createOrderRequiresIdempotencyKey() {
        assertThatThrownBy(() -> service.createOrder(createRequest("prod-1"), customerPrincipal(), " "))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void sameKeyAndSameBodyReturnsStoredResponseWithoutCreatingSecondOrder() throws Exception {
        User customer = customerUser();
        CreateOrderRequest request = createRequest("prod-1");
        JwtPrincipal principal = customerPrincipal();
        AtomicReference<IdempotencyRecord> storedRecord = new AtomicReference<>();

        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(idempotencyRepository.saveAndFlush(any(IdempotencyRecord.class))).thenAnswer(invocation -> {
            IdempotencyRecord record = invocation.getArgument(0);
            storedRecord.set(record);
            return record;
        });
        when(idempotencyRepository.findById(any(UUID.class))).thenAnswer(invocation -> Optional.of(storedRecord.get()));
        when(orderService.createOrder(request, principal)).thenReturn(orderResponse());

        ResponseEntity<String> firstResponse = service.createOrder(request, principal, "order-key-1");

        reset(idempotencyRepository);
        when(idempotencyRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(idempotencyRepository.findByUserIdAndIdempotencyKey(CUSTOMER_ID, "order-key-1"))
                .thenReturn(Optional.of(storedRecord.get()));

        ResponseEntity<String> replayedResponse = service.createOrder(request, principal, "order-key-1");

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replayedResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replayedResponse.getBody()).isEqualTo(firstResponse.getBody());

        JsonNode replayedJson = objectMapper.readTree(replayedResponse.getBody());
        assertThat(replayedJson.get("id").asText()).isEqualTo(ORDER_ID.toString());
        verify(orderService, times(1)).createOrder(request, principal);
    }

    @Test
    void sameKeyAndDifferentBodyReturnsConflict() {
        User customer = customerUser();
        CreateOrderRequest originalRequest = createRequest("prod-1");
        CreateOrderRequest differentRequest = createRequest("prod-2");
        IdempotencyRecord originalRecord = insertAndCaptureInProgressRecord(originalRequest, customer);

        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(idempotencyRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(idempotencyRepository.findByUserIdAndIdempotencyKey(CUSTOMER_ID, "order-key-2"))
                .thenReturn(Optional.of(originalRecord));

        assertThatThrownBy(() -> service.createOrder(differentRequest, customerPrincipal(), "order-key-2"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessage("Idempotency key was already used with a different request");

        verify(orderService, never()).createOrder(any(), any());
    }

    @Test
    void inProgressConcurrentRequestReturnsConflictWithRetryAfter() {
        User customer = customerUser();
        CreateOrderRequest request = createRequest("prod-1");
        IdempotencyRecord inProgressRecord = insertAndCaptureInProgressRecord(request, customer);

        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(idempotencyRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(idempotencyRepository.findByUserIdAndIdempotencyKey(CUSTOMER_ID, "order-key-3"))
                .thenReturn(Optional.of(inProgressRecord));

        assertThatThrownBy(() -> service.createOrder(request, customerPrincipal(), "order-key-3"))
                .isInstanceOf(IdempotencyConflictException.class)
                .satisfies(error -> assertThat(((IdempotencyConflictException) error).getRetryAfterSeconds()).isEqualTo(5));

        verify(orderService, never()).createOrder(any(), any());
    }

    private IdempotencyRecord insertAndCaptureInProgressRecord(CreateOrderRequest request, User customer) {
        AtomicReference<IdempotencyRecord> capturedRecord = new AtomicReference<>();
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(idempotencyRepository.saveAndFlush(any(IdempotencyRecord.class))).thenAnswer(invocation -> {
            IdempotencyRecord record = invocation.getArgument(0);
            capturedRecord.set(record);
            return record;
        });
        when(idempotencyRepository.findById(any(UUID.class))).thenAnswer(invocation -> Optional.of(capturedRecord.get()));
        when(orderService.createOrder(request, customerPrincipal())).thenThrow(new RuntimeException("stop after hash capture"));

        assertThatThrownBy(() -> service.createOrder(request, customerPrincipal(), "hash-capture-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("stop after hash capture");

        reset(idempotencyRepository, userRepository, orderService);
        return new IdempotencyRecord(
                UUID.randomUUID(),
                customer,
                "existing-key",
                capturedRecord.get().getRequestHash(),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private CreateOrderRequest createRequest(String productId) {
        return new CreateOrderRequest(List.of(
                new CreateOrderItemRequest(productId, "Keyboard", 1, new BigDecimal("25.00"))
        ));
    }

    private OrderResponse orderResponse() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        return new OrderResponse(
                ORDER_ID,
                CUSTOMER_ID,
                OrderStatus.PENDING,
                "USD",
                new BigDecimal("25.00"),
                createdAt,
                createdAt,
                List.of()
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

    private static class NoOpTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
