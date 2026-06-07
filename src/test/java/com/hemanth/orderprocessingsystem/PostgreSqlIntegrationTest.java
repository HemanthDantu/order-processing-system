package com.hemanth.orderprocessingsystem;

import com.hemanth.orderprocessingsystem.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs a small set of high-signal checks against real PostgreSQL.
 *
 * <p>Most tests use mocks for speed. This class covers the database behaviors
 * that mocks cannot prove: Flyway migration compatibility, deterministic seed
 * data, check constraints, and the idempotency unique constraint.</p>
 */
@Testcontainers
@SpringBootTest(properties = {
        "order.processing.scheduler.fixed-rate-ms=600000"
})
class PostgreSqlIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywaySeedDataIsAvailableAndJpaMappingsValidate() {
        assertThat(userRepository.findByUsername("customer1")).isPresent();
        assertThat(userRepository.findByUsername("admin1")).isPresent();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Long.class)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void systemHistoryRowsCannotReferenceAChangedByUser() {
        UUID orderId = jdbcTemplate.queryForObject("SELECT id FROM orders LIMIT 1", UUID.class);
        UUID customerId = userRepository.findByUsername("customer1").orElseThrow().getId();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                        INSERT INTO order_status_history (
                            id,
                            order_id,
                            from_status,
                            to_status,
                            changed_by,
                            changed_by_type,
                            changed_at,
                            reason
                        )
                        VALUES (?, ?, NULL, 'PROCESSING', ?, 'SYSTEM', ?, 'Invalid system actor')
                        """,
                UUID.randomUUID(),
                orderId,
                customerId,
                Timestamp.from(Instant.now())
        )).isInstanceOf(DataAccessException.class);
    }

    @Test
    void idempotencyKeyIsUniquePerUser() {
        UUID customerId = userRepository.findByUsername("customer1").orElseThrow().getId();
        String idempotencyKey = "integration-duplicate-key";
        Instant now = Instant.now();

        insertIdempotencyRecord(customerId, idempotencyKey, now);

        assertThatThrownBy(() -> insertIdempotencyRecord(customerId, idempotencyKey, now))
                .isInstanceOf(DataAccessException.class);
    }

    private void insertIdempotencyRecord(UUID userId, String idempotencyKey, Instant createdAt) {
        jdbcTemplate.update("""
                        INSERT INTO idempotency_keys (
                            id,
                            user_id,
                            idempotency_key,
                            request_hash,
                            status,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?)
                        """,
                UUID.randomUUID(),
                userId,
                idempotencyKey,
                UUID.randomUUID().toString(),
                Timestamp.from(createdAt)
        );
    }
}
