package com.hemanth.orderprocessingsystem.processing;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the SQL contract used by the PostgreSQL scheduled processor.
 */
class OrderProcessingRepositoryTest {

    @Test
    void claimPendingOrderIdsUsesForUpdateSkipLockedAndBatchLimit() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        OrderProcessingRepository repository = new OrderProcessingRepository(jdbcTemplate);
        UUID orderId = UUID.randomUUID();

        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class),
                eq(UUID.class)
        )).thenReturn(List.of(orderId));

        List<UUID> result = repository.claimPendingOrderIds(500);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parametersCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), parametersCaptor.capture(), eq(UUID.class));

        assertThat(result).containsExactly(orderId);
        assertThat(sqlCaptor.getValue()).contains("FOR UPDATE SKIP LOCKED");
        assertThat(sqlCaptor.getValue()).contains("LIMIT :batchSize");
        assertThat(parametersCaptor.getValue().getValue("batchSize")).isEqualTo(500);
    }

    @Test
    void markProcessingSkipsDatabaseCallWhenNoOrdersClaimed() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        OrderProcessingRepository repository = new OrderProcessingRepository(jdbcTemplate);

        List<UUID> updatedIds = repository.markProcessing(List.of(), Instant.now());

        assertThat(updatedIds).isEmpty();
        verify(jdbcTemplate, never()).queryForList(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource.class),
                eq(UUID.class)
        );
    }

    @Test
    void insertScheduledProcessingHistoryUsesSystemActorWithNullChangedBy() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        OrderProcessingRepository repository = new OrderProcessingRepository(jdbcTemplate);

        repository.insertScheduledProcessingHistory(List.of(UUID.randomUUID()), Instant.now());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).batchUpdate(
                sqlCaptor.capture(),
                org.mockito.ArgumentMatchers.any(MapSqlParameterSource[].class)
        );

        assertThat(sqlCaptor.getValue()).contains("NULL");
        assertThat(sqlCaptor.getValue()).contains("'SYSTEM'");
        assertThat(sqlCaptor.getValue()).contains("'Scheduled processing'");
    }
}
