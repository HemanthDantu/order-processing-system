package com.hemanth.orderprocessingsystem.processing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the scheduled order-processing job.
 */
class OrderProcessingJobServiceTest {

    @Test
    void processPendingOrdersMovesOnlyClaimedBatchAndWritesHistory() {
        OrderProcessingRepository repository = mock(OrderProcessingRepository.class);
        OrderProcessingSchedulerProperties properties = new OrderProcessingSchedulerProperties();
        properties.setBatchSize(2);
        OrderProcessingJobService service = new OrderProcessingJobService(repository, properties);
        List<UUID> claimedOrderIds = List.of(UUID.randomUUID(), UUID.randomUUID());

        when(repository.claimPendingOrderIds(2)).thenReturn(claimedOrderIds);
        when(repository.markProcessing(eq(claimedOrderIds), any(Instant.class))).thenReturn(claimedOrderIds);

        OrderProcessingJobResponse response = service.processPendingOrders();

        assertThat(response.processedCount()).isEqualTo(2);
        assertThat(response.durationMs()).isNotNegative();
        assertThat(response.startedAt()).isBeforeOrEqualTo(response.completedAt());
        verify(repository).claimPendingOrderIds(2);
        verify(repository).markProcessing(eq(claimedOrderIds), any(Instant.class));
        verify(repository).insertScheduledProcessingHistory(eq(claimedOrderIds), any(Instant.class));
    }

    @Test
    void processPendingOrdersRespectsBatchSizeMinimum() {
        OrderProcessingRepository repository = mock(OrderProcessingRepository.class);
        OrderProcessingSchedulerProperties properties = new OrderProcessingSchedulerProperties();
        properties.setBatchSize(0);
        OrderProcessingJobService service = new OrderProcessingJobService(repository, properties);

        when(repository.claimPendingOrderIds(1)).thenReturn(List.of());
        when(repository.markProcessing(eq(List.of()), any(Instant.class))).thenReturn(List.of());

        OrderProcessingJobResponse response = service.processPendingOrders();

        assertThat(response.processedCount()).isZero();
        verify(repository).claimPendingOrderIds(1);
        verify(repository).markProcessing(eq(List.of()), any(Instant.class));
    }

    @Test
    void processPendingOrdersLogsAndPropagatesFailures() {
        OrderProcessingRepository repository = mock(OrderProcessingRepository.class);
        OrderProcessingSchedulerProperties properties = new OrderProcessingSchedulerProperties();
        OrderProcessingJobService service = new OrderProcessingJobService(repository, properties);

        when(repository.claimPendingOrderIds(500)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(service::processPendingOrders)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(repository, never()).markProcessing(any(), any());
        verify(repository, never()).insertScheduledProcessingHistory(any(), any());
    }
}
