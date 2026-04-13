package com.eaglepoint.exam.scheduling.repository;

import com.eaglepoint.exam.scheduling.model.ExamSessionClass;
import com.eaglepoint.exam.scheduling.model.ExamSessionClassId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA repository for {@link ExamSessionClass} join entities.
 */
@Repository
public interface ExamSessionClassRepository extends JpaRepository<ExamSessionClass, ExamSessionClassId> {

    List<ExamSessionClass> findByExamSessionId(Long examSessionId);

    void deleteByExamSessionId(Long examSessionId);
}
