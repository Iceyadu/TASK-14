package com.eaglepoint.exam.scheduling.repository;

import com.eaglepoint.exam.scheduling.model.Term;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link Term} entities.
 */
@Repository
public interface TermRepository extends JpaRepository<Term, Long> {

    List<Term> findByIsActiveTrue();

    Optional<Term> findFirstByNameIgnoreCase(String name);
}
