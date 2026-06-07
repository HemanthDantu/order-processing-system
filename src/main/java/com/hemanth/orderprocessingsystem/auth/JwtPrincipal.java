package com.hemanth.orderprocessingsystem.auth;

import com.hemanth.orderprocessingsystem.user.UserRole;

import java.util.UUID;

/**
 * Authenticated user details derived from a JWT.
 *
 * <p>This is the in-memory identity representation used by controllers and
 * services after the token has already been verified.</p>
 *
 * @param userId the authenticated user's id
 * @param username the authenticated username
 * @param role the authenticated role
 */
public record JwtPrincipal(
        UUID userId,
        String username,
        UserRole role
) {
}
