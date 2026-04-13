package com.eaglepoint.exam.security.repository;

import com.eaglepoint.exam.security.model.NonceReplay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Repository for nonce replay prevention records.
 */
@Repository
public interface NonceReplayRepository extends JpaRepository<NonceReplay, String> {

    /**
     * Deletes all expired nonces to keep the table small.
     */
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}
