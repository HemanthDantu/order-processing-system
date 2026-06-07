package com.hemanth.orderprocessingsystem.processing;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JDBC repository for the scheduled pending-order processing batch.
 */
@Repository
public class OrderProcessingRepository {

    private static final String CLAIM_PENDING_ORDER_IDS_SQL = """
            SELECT id
            FROM orders
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """;

    private static final String MARK_PROCESSING_SQL = """
            UPDATE orders
            SET status = 'PROCESSING',
                updated_at = :updatedAt,
                version = version + 1
            WHERE id IN (:orderIds)
              AND status = 'PENDING'
            RETURNING id
            """;

    private static final String INSERT_SYSTEM_HISTORY_SQL = """
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
            VALUES (
                :id,
                :orderId,
                'PENDING',
                'PROCESSING',
                NULL,
                'SYSTEM',
                :changedAt,
                'Scheduled processing'
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OrderProcessingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Claims pending orders using PostgreSQL row locks while skipping rows that
     * another scheduler instance has already locked.
     */
    public List<UUID> claimPendingOrderIds(int batchSize) {
        return jdbcTemplate.queryForList(
                CLAIM_PENDING_ORDER_IDS_SQL,
                new MapSqlParameterSource("batchSize", batchSize),
                UUID.class
        );
    }

    /**
     * Moves claimed pending orders to PROCESSING.
     */
    public List<UUID> markProcessing(List<UUID> orderIds, Instant updatedAt) {
        if (orderIds.isEmpty()) {
            return List.of();
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("orderIds", orderIds)
                .addValue("updatedAt", Timestamp.from(updatedAt));
        return jdbcTemplate.queryForList(MARK_PROCESSING_SQL, parameters, UUID.class);
    }

    /**
     * Inserts system audit rows for orders moved by the scheduler.
     */
    public void insertScheduledProcessingHistory(List<UUID> orderIds, Instant changedAt) {
        if (orderIds.isEmpty()) {
            return;
        }

        MapSqlParameterSource[] batch = orderIds.stream()
                .map(orderId -> new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("orderId", orderId)
                        .addValue("changedAt", Timestamp.from(changedAt)))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(INSERT_SYSTEM_HISTORY_SQL, batch);
    }
}
