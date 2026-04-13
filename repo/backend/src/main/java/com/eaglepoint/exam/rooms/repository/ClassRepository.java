package com.eaglepoint.exam.rooms.repository;

import com.eaglepoint.exam.rooms.model.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link SchoolClass} entities.
 */
@Repository
public interface ClassRepository extends JpaRepository<SchoolClass, Long> {

    List<SchoolClass> findByGradeIdAndCampusId(Long gradeId, Long campusId);

    Optional<SchoolClass> findFirstByNameIgnoreCase(String name);
}
