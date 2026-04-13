package com.eaglepoint.exam.rooms.repository;

import com.eaglepoint.exam.rooms.model.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for {@link Grade} entities.
 */
@Repository
public interface GradeRepository extends JpaRepository<Grade, Long> {
}
