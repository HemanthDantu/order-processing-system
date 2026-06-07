package com.hemanth.orderprocessingsystem.auth;

/**
 * Login response payload.
 *
 * <p>Clients can use {@code tokenType} as the Authorization header prefix
 * without hard-coding the string in multiple places.</p>
 *
 * @param accessToken the issued JWT
 * @param tokenType the token type, typically Bearer
 */
public record LoginResponse(
        String accessToken,
        String tokenType
) {
}
