package com.hemanth.orderprocessingsystem.auth;

import com.hemanth.orderprocessingsystem.user.User;
import com.hemanth.orderprocessingsystem.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Handles JWT creation and parsing.
 *
 * <p>The service centralizes token format details, signing, claim extraction,
 * and expiration handling so the rest of the application only deals with a
 * simple principal object.</p>
 */
@Service
public class JwtService {

    private final String secret;
    private final long expirationMinutes;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes
    ) {
        this.secret = secret;
        this.expirationMinutes = expirationMinutes;
    }

    /**
     * Creates a signed JWT for the authenticated user.
     *
     * <p>The token includes user id, username, and role because those are the
     * fields needed later for authorization and ownership checks.</p>
     */
    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("userId", user.getId().toString())
                .claim("role", user.getRole().name())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Parses a token and verifies its signature.
     *
     * @param token raw JWT string without the Bearer prefix
     * @return verified claims
     */
    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Convenience validation method used when only success or failure matters.
     *
     * @param token raw JWT string
     * @return {@code true} if parsing succeeds, otherwise {@code false}
     */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * Converts verified claims into a lightweight application principal.
     *
     * @param token raw JWT string
     * @return principal extracted from the verified token
     */
    public JwtPrincipal extractPrincipal(String token) {
        Claims claims = parseClaims(token);
        String username = claims.getSubject();
        UUID userId = UUID.fromString(claims.get("userId", String.class));
        UserRole role = UserRole.valueOf(claims.get("role", String.class));
        return new JwtPrincipal(userId, username, role);
    }

    /**
     * Builds the HMAC key from the configured secret.
     *
     * <p>The secret should be long enough for HS256, otherwise token signing
     * fails fast instead of producing a weak key.</p>
     */
    private SecretKey signingKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
