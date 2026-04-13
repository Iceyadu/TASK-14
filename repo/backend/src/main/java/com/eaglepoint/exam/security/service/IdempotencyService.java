package com.eaglepoint.exam.security.service;

import com.eaglepoint.exam.security.model.IdempotencyKey;
import com.eaglepoint.exam.security.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service that provides idempotency guarantees for write operations.
 * <p>
 * When a client retries a request with the same idempotency key, the
 * previously stored response is returned instead of re-executing the
 * operation.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final long EXPIRY_HOURS = 24;

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Checks whether an idempotency key already exists for the given user and
     * operation. If it does and is not expired, returns the stored response.
     *
     * @param key           the idempotency key from the client
     * @param userId        the authenticated user's id
     * @param operationType a label for the operation (e.g. "CREATE_SESSION")
     * @return the previously stored response object, or {@code null} if no
     *         matching key exists
     */
    @Transactional(readOnly = true)
    public Object checkAndStore(String key, Long userId, String operationType) {
        Optional<IdempotencyKey> existing = repository
                .findByIdempotencyKeyAndUserIdAndOperationType(key, userId, operationType);

        if (existing.isEmpty()) {
            return null;
        }

        IdempotencyKey record = existing.get();

        // Check expiry
        if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }

        // Deserialise stored response
        String json = record.getResponseJson();
        if (json == null) {
            return null;
        }

        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialise stored idempotency response for key={}", key, e);
            return null;
        }
    }

    /**
     * Stores a response for the given idempotency key so that subsequent
     * requests with the same key return the same result.
     *
     * @param key           the idempotency key
     * @param userId        the authenticated user's id
     * @param operationType a label for the operation
     * @param response      the response object to store (will be serialised to JSON)
     */
    @Transactional
    public void storeResponse(String key, Long userId, String operationType, Object response) {
        String responseJson;
        try {
            responseJson = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise response for idempotency key={}", key, e);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        IdempotencyKey record = new IdempotencyKey(
                key,
                userId,
                operationType,
                responseJson,
                now,
                now.plusHours(EXPIRY_HOURS)
        );
        repository.save(record);
    }
}
