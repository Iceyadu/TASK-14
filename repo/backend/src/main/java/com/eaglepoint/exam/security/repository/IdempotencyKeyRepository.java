package com.eaglepoint.exam.security.repository;

import com.eaglepoint.exam.security.model.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for idempotency key records.
 */
@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    /**
     * Looks up an existing idempotency key for the given user and operation.
     */
    Optional<IdempotencyKey> findByIdempotencyKeyAndUserIdAndOperationType(
            String idempotencyKey, Long userId, String operationType);

    /**
     * Deletes all expired idempotency keys.
     */
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}
