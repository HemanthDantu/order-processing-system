package com.hemanth.orderprocessingsystem.exception;

import java.time.Instant;
import java.util.List;

/**
 * Standard API error response body.
 *
 * @param timestamp when the error response was created
 * @param status HTTP status code
 * @param error short HTTP error name
 * @param message readable error summary
 * @param path request path
 * @param details optional field-level validation messages
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {
}
