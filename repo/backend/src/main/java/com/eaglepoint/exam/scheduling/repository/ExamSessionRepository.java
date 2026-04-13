package com.eaglepoint.exam.scheduling.repository;

import com.eaglepoint.exam.scheduling.model.ExamSession;
import com.eaglepoint.exam.scheduling.model.ExamSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link ExamSession} with specification support for dynamic filtering.
 */
@Repository
public interface ExamSessionRepository extends JpaRepository<ExamSession, Long>, JpaSpecificationExecutor<ExamSession> {

    Page<ExamSession> findByTermIdAndStatus(Long termId, ExamSessionStatus status, Pageable pageable);

    Optional<ExamSession> findByIdAndStatus(Long id, ExamSessionStatus status);

    Page<ExamSession> findByTermId(Long termId, Pageable pageable);

    Page<ExamSession> findByCampusId(Long campusId, Pageable pageable);

    /**
     * Find published sessions for a student by looking up which classes they are enrolled in
     * through the exam_session_classes join table and roster_entries table.
     */
    @Query("SELECT es FROM ExamSession es " +
           "JOIN ExamSessionClass esc ON esc.examSessionId = es.id " +
           "JOIN RosterEntry re ON re.classId = esc.classId AND re.termId = es.termId " +
           "WHERE re.studentUserId = :studentUserId AND re.isDeleted = false AND es.status = 'PUBLISHED'")
    List<ExamSession> findPublishedSessionsForStudent(@Param("studentUserId") Long studentUserId);
}
