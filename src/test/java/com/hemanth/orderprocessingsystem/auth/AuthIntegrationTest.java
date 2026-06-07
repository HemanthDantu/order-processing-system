package com.hemanth.orderprocessingsystem.auth;

import com.hemanth.orderprocessingsystem.history.OrderStatusHistoryRepository;
import com.hemanth.orderprocessingsystem.idempotency.IdempotencyRepository;
import com.hemanth.orderprocessingsystem.order.OrderRepository;
import com.hemanth.orderprocessingsystem.processing.OrderProcessingRepository;
import com.hemanth.orderprocessingsystem.user.User;
import com.hemanth.orderprocessingsystem.user.UserRepository;
import com.hemanth.orderprocessingsystem.user.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the JWT login and bearer auth flow end-to-end with mocked persistence.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration," +
                "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
})
@AutoConfigureMockMvc
@Import(AuthIntegrationTest.SecuredTestController.class)
class AuthIntegrationTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @MockitoBean
    private IdempotencyRepository idempotencyRepository;

    @MockitoBean
    private PlatformTransactionManager platformTransactionManager;

    @MockitoBean
    private OrderProcessingRepository orderProcessingRepository;

    @Test
    void customer1CanLogin() throws Exception {
        when(userRepository.findByUsername("customer1")).thenReturn(Optional.of(customerUser()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("customer1", "password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void invalidCredentialsReturn401() throws Exception {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("unknown", "password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Invalid username or password")))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/login")));
    }

    @Test
    void missingBearerTokenReturnsConsistent401Json() throws Exception {
        mockMvc.perform(get("/api/v1/secured-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", is("Authentication is required")))
                .andExpect(jsonPath("$.path", is("/api/v1/secured-test")));
    }

    @Test
    void bearerTokenAllowsSecuredEndpointAccess() throws Exception {
        when(userRepository.findByUsername("customer1")).thenReturn(Optional.of(customerUser()));

        String token = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("customer1", "password"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .get("accessToken")
                .asText();

        mockMvc.perform(get("/api/v1/secured-test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("customer1")))
                .andExpect(jsonPath("$.role", is("CUSTOMER")));
    }

    private User customerUser() {
        return new User(
                CUSTOMER_ID,
                "customer1",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password"),
                UserRole.CUSTOMER,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    @RestController
    @RequestMapping("/api/v1")
    static class SecuredTestController {

        @GetMapping("/secured-test")
        public JwtPrincipal secured(Authentication authentication) {
            return (JwtPrincipal) authentication.getPrincipal();
        }
    }
}
