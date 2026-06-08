package com.hemanth.orderprocessingsystem.auth;

import com.hemanth.orderprocessingsystem.user.User;
import com.hemanth.orderprocessingsystem.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authentication use cases.
 *
 * <p>This service verifies credentials and delegates token generation to
 * {@link JwtService}; it does not know anything about HTTP.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Validates the supplied username and password, then returns a JWT.
     *
     * @param request login credentials from the client
     * @return bearer-token response payload
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> {
                    log.warn("Login failed for username={} reason=user_not_found", request.username());
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
                });

        // Never log or expose the submitted password; BCrypt compares it to the stored hash safely.
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed for username={} reason=password_mismatch", request.username());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        log.info("Login succeeded for userId={} username={} role={}", user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(jwtService.generateToken(user), "Bearer");
    }
}
