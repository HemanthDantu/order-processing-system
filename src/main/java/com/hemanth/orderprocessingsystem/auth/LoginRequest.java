package com.hemanth.orderprocessingsystem.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Login request payload.
 *
 * <p>Keeping the payload narrow makes the authentication contract easy to
 * understand and straightforward to validate.</p>
 *
 * @param username the login username
 * @param password the raw password
 */
public record LoginRequest(
        @NotBlank(message = "Username is required")
        String username,
        @NotBlank(message = "Password is required")
        String password
) {
}
