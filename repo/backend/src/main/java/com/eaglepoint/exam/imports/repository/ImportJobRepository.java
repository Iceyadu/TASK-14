package com.eaglepoint.exam.imports.repository;

import com.eaglepoint.exam.imports.model.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for {@link ImportJob}.
 */
@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
}
