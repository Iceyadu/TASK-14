package com.eaglepoint.exam.security.repository;

import com.eaglepoint.exam.security.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for session lookup and management.
 */
@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findBySessionToken(String sessionToken);

    List<Session> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
