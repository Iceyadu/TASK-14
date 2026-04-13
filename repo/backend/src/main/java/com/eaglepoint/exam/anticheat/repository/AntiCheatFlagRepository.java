package com.eaglepoint.exam.anticheat.repository;

import com.eaglepoint.exam.anticheat.model.AntiCheatFlag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JPA repository for {@link AntiCheatFlag}.
 */
@Repository
public interface AntiCheatFlagRepository extends JpaRepository<AntiCheatFlag, Long> {

    Page<AntiCheatFlag> findByStatus(String status, Pageable pageable);

    List<AntiCheatFlag> findByStudentUserId(Long studentUserId);

    /**
     * Counts how many exam results a student submitted within a time window,
     * using a native query against the audit log as a proxy for exam submissions.
     */
    @Query(value = "SELECT COUNT(*) FROM audit_log a WHERE a.user_id = :studentUserId " +
            "AND a.action = 'SUBMIT_EXAM_RESULT' " +
            "AND a.timestamp >= :since",
            nativeQuery = true)
    long countRecentExamSubmissions(@Param("studentUserId") Long studentUserId,
                                    @Param("since") LocalDateTime since);
}
