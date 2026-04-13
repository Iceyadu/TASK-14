package com.eaglepoint.exam.audit.repository;

import com.eaglepoint.exam.audit.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JPA repository for {@link AuditLog} entities with specification support
 * for dynamic filtering.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByUserId(Long userId, Pageable pageable);

    Page<AuditLog> findByEntityType(String entityType, Pageable pageable);

    Page<AuditLog> findByAction(String action, Pageable pageable);

    Page<AuditLog> findByTimestampBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<AuditLog> findByUserIdAndEntityType(Long userId, String entityType, Pageable pageable);

    Page<AuditLog> findByUserIdAndAction(Long userId, String action, Pageable pageable);

    Page<AuditLog> findByEntityTypeAndAction(String entityType, String action, Pageable pageable);

    /**
     * Ordered ascending by time for consecutive-score / duplicate-detection analysis.
     */
    List<AuditLog> findByUserIdAndActionAndTimestampGreaterThanEqualOrderByTimestampAsc(
            Long userId, String action, LocalDateTime since);
}
