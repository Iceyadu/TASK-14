package com.eaglepoint.exam.rooms.repository;

import com.eaglepoint.exam.rooms.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for {@link Course} entities.
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
}
