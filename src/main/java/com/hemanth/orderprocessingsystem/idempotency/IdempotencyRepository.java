package com.hemanth.orderprocessingsystem.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for idempotency key records.
 */
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    /**
     * Loads a previously attempted request after an insert-first unique
     * constraint conflict.
     */
    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
