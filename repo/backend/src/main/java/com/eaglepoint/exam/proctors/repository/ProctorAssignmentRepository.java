package com.eaglepoint.exam.proctors.repository;

import com.eaglepoint.exam.proctors.model.ProctorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA repository for {@link ProctorAssignment} entities.
 */
@Repository
public interface ProctorAssignmentRepository extends JpaRepository<ProctorAssignment, Long> {

    List<ProctorAssignment> findByExamSessionId(Long examSessionId);
}
