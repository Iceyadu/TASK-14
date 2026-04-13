package com.eaglepoint.exam.rooms.repository;

import com.eaglepoint.exam.rooms.model.Campus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for {@link Campus} entities.
 */
@Repository
public interface CampusRepository extends JpaRepository<Campus, Long> {
}
