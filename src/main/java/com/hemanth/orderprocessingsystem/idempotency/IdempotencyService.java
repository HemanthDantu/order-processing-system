package com.hemanth.orderprocessingsystem.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hemanth.orderprocessingsystem.auth.JwtPrincipal;
import com.hemanth.orderprocessingsystem.exception.IdempotencyConflictException;
import com.hemanth.orderprocessingsystem.order.OrderService;
import com.hemanth.orderprocessingsystem.order.dto.CreateOrderRequest;
import com.hemanth.orderprocessingsystem.order.dto.OrderResponse;
import com.hemanth.orderprocessingsystem.user.User;
import com.hemanth.orderprocessingsystem.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates idempotent order creation using an insert-first database record.
 *
 * <p>Transaction trade-off: the IN_PROGRESS record is committed in its own
 * transaction before order creation so concurrent duplicate requests can return
 * a Retry-After conflict instead of racing to create another order. The order is
 * then created through {@link OrderService}'s normal transaction. After that
 * transaction commits, this service serializes and stores the HTTP response in a
 * final short transaction. If the process crashes after order commit but before
 * response persistence, the key can remain IN_PROGRESS/FAILED even though the
 * order exists. Production systems usually close that gap with an outbox or
 * recovery job; for this assignment, the trade-off is explicit and localized.</p>
 */
@Service
public class IdempotencyService {

    private static final int RETRY_AFTER_SECONDS = 5;

    private final IdempotencyRepository idempotencyRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final ObjectWriter canonicalRequestWriter;
    private final ObjectWriter responseWriter;
    private final TransactionTemplate requiresNewTransaction;

    public IdempotencyService(
            IdempotencyRepository idempotencyRepository,
            UserRepository userRepository,
            OrderService orderService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.idempotencyRepository = idempotencyRepository;
        this.userRepository = userRepository;
        this.orderService = orderService;
        this.responseWriter = objectMapper.writer();

        ObjectMapper canonicalMapper = objectMapper.copy();
        canonicalMapper.setConfig(canonicalMapper.getSerializationConfig().with(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY));
        canonicalMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.canonicalRequestWriter = canonicalMapper.writer();

        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Creates an order once for a user/idempotency-key pair and replays the
     * stored response for safe retries.
     */
    public ResponseEntity<String> createOrder(
            CreateOrderRequest request,
            JwtPrincipal principal,
            String idempotencyKey
    ) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }

        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
        String requestHash = hashNormalizedOrderRequest(request);
        IdempotencyAttempt attempt = beginAttempt(user, idempotencyKey.trim(), requestHash);

        if (attempt.replayResponse()) {
            return jsonResponse(attempt.statusCode(), attempt.responseBody(), attempt.resourceId());
        }

        try {
            OrderResponse orderResponse = orderService.createOrder(request, principal);
            String responseBody = responseWriter.writeValueAsString(orderResponse);
            completeAttempt(attempt.recordId(), responseBody, HttpStatus.CREATED.value(), orderResponse.id());
            return jsonResponse(HttpStatus.CREATED.value(), responseBody, orderResponse.id());
        } catch (JsonProcessingException exception) {
            markFailed(attempt.recordId());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize idempotent order response", exception);
        } catch (RuntimeException exception) {
            markFailed(attempt.recordId());
            throw exception;
        }
    }

    private IdempotencyAttempt beginAttempt(User user, String idempotencyKey, String requestHash) {
        IdempotencyRecord record = new IdempotencyRecord(UUID.randomUUID(), user, idempotencyKey, requestHash, Instant.now());

        try {
            IdempotencyRecord inserted = requiresNewTransaction.execute(status -> idempotencyRepository.saveAndFlush(record));
            return IdempotencyAttempt.started(inserted.getId());
        } catch (DataIntegrityViolationException exception) {
            IdempotencyRecord existing = idempotencyRepository
                    .findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey)
                    .orElseThrow(() -> new IdempotencyConflictException("Idempotency key is currently unavailable", RETRY_AFTER_SECONDS));
            return handleExistingAttempt(existing, requestHash);
        }
    }

    private IdempotencyAttempt handleExistingAttempt(IdempotencyRecord existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException("Idempotency key was already used with a different request");
        }

        return switch (existing.getStatus()) {
            case IN_PROGRESS -> throw new IdempotencyConflictException(
                    "An order creation request with this Idempotency-Key is still in progress",
                    RETRY_AFTER_SECONDS
            );
            case COMPLETED -> IdempotencyAttempt.replay(
                    existing.getStatusCode(),
                    existing.getResponseBody(),
                    existing.getResourceId()
            );
            case FAILED -> throw new IdempotencyConflictException(
                    "Previous order creation request with this Idempotency-Key failed; retry with a new key"
            );
        };
    }

    private void completeAttempt(UUID recordId, String responseBody, int statusCode, UUID resourceId) {
        requiresNewTransaction.executeWithoutResult(status -> {
            IdempotencyRecord record = idempotencyRepository.findById(recordId)
                    .orElseThrow(() -> new IllegalStateException("Idempotency record disappeared before completion"));
            record.markCompleted(responseBody, statusCode, resourceId, Instant.now());
            idempotencyRepository.save(record);
        });
    }

    private void markFailed(UUID recordId) {
        requiresNewTransaction.executeWithoutResult(status -> idempotencyRepository.findById(recordId).ifPresent(record -> {
            record.markFailed(Instant.now());
            idempotencyRepository.save(record);
        }));
    }

    private String hashNormalizedOrderRequest(CreateOrderRequest request) {
        List<NormalizedOrderItem> normalizedItems = request.items()
                .stream()
                .map(item -> new NormalizedOrderItem(
                        item.productId(),
                        item.productName(),
                        item.quantity(),
                        item.unitPrice().setScale(2, java.math.RoundingMode.HALF_UP)
                ))
                .toList();

        try {
            String canonicalJson = canonicalRequestWriter.writeValueAsString(new NormalizedOrderRequest(normalizedItems));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to normalize order request", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private ResponseEntity<String> jsonResponse(int statusCode, String responseBody, UUID resourceId) {
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity
                .status(statusCode)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (resourceId != null && statusCode == HttpStatus.CREATED.value()) {
            responseBuilder.location(URI.create("/api/v1/orders/" + resourceId));
        }

        return responseBuilder.body(responseBody);
    }

    private record IdempotencyAttempt(
            UUID recordId,
            boolean replayResponse,
            Integer statusCode,
            String responseBody,
            UUID resourceId
    ) {
        static IdempotencyAttempt started(UUID recordId) {
            return new IdempotencyAttempt(recordId, false, null, null, null);
        }

        static IdempotencyAttempt replay(Integer statusCode, String responseBody, UUID resourceId) {
            if (statusCode == null || responseBody == null) {
                throw new IdempotencyConflictException("Stored idempotent response is incomplete; retry with a new key");
            }
            return new IdempotencyAttempt(null, true, statusCode, responseBody, resourceId);
        }
    }

    private record NormalizedOrderRequest(List<NormalizedOrderItem> items) {
    }

    private record NormalizedOrderItem(String productId, String productName, Integer quantity, BigDecimal unitPrice) {
    }
}
