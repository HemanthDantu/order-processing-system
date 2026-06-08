package com.hemanth.orderprocessingsystem.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Converts common application exceptions into consistent JSON responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Returns a 400 response for request validation failures.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        log.debug("Validation failed path={} errorCount={}", request.getRequestURI(), exception.getBindingResult().getErrorCount());
        List<String> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Request validation failed: " + String.join("; ", details),
                request.getRequestURI()
        );
    }

    /**
     * Preserves explicit HTTP status choices thrown by services.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        log.debug("ResponseStatusException handled path={} status={}", request.getRequestURI(), status.value());
        return buildResponse(status, safeMessage(exception.getReason(), status), request.getRequestURI());
    }

    /**
     * Returns a 400 response when a requested order status transition is invalid.
     */
    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidOrderStateException(
            InvalidOrderStateException exception,
            HttpServletRequest request
    ) {
        log.debug("Invalid order transition path={} message={}", request.getRequestURI(), exception.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request.getRequestURI());
    }

    /**
     * Returns a 409 response when optimistic locking detects a concurrent update.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        log.warn("Optimistic locking conflict path={}", request.getRequestURI());
        return buildResponse(
                HttpStatus.CONFLICT,
                "Order was updated by another request. Please retry with the latest state.",
                request.getRequestURI()
        );
    }

    /**
     * Returns a 409 response when an idempotency key cannot be reused safely.
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException exception,
            HttpServletRequest request
    ) {
        log.info(
                "Idempotency conflict path={} retryAfterSeconds={}",
                request.getRequestURI(),
                exception.getRetryAfterSeconds()
        );
        ResponseEntity<ApiErrorResponse> response = buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request.getRequestURI()
        );

        if (exception.getRetryAfterSeconds() == null) {
            return response;
        }

        return ResponseEntity
                .status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, exception.getRetryAfterSeconds().toString())
                .body(response.getBody());
    }

    /**
     * Returns a 401 response for authentication failures.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(
            BadCredentialsException exception,
            HttpServletRequest request
    ) {
        log.warn("Authentication failed path={}", request.getRequestURI());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid username or password", request.getRequestURI());
    }

    /**
     * Returns a 403 response when an authenticated user lacks permission.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        log.warn("Access denied path={}", request.getRequestURI());
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied", request.getRequestURI());
    }

    /**
     * Returns a safe response for database constraint failures without leaking
     * table names, constraint names, SQL, or stack traces.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = isUniqueConstraintViolation(exception) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        log.warn("Database constraint violation path={} mappedStatus={}", request.getRequestURI(), status.value());
        String message = status == HttpStatus.CONFLICT
                ? "A resource with the same unique value already exists"
                : "Request violates a database constraint";
        return buildResponse(status, message, request.getRequestURI());
    }

    /**
     * Returns a generic 500 response for unhandled failures.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unexpected server error path={}", request.getRequestURI(), exception);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request.getRequestURI());
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String message,
            String path
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path
        );
        return ResponseEntity.status(status).body(response);
    }

    private String safeMessage(String reason, HttpStatus status) {
        if (reason == null || reason.isBlank()) {
            return status.getReasonPhrase();
        }
        return reason;
    }

    private boolean isUniqueConstraintViolation(Throwable throwable) {
        String message = throwable.getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains("duplicate")) {
            return true;
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
