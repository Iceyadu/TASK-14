package com.eaglepoint.exam.imports.repository;

import com.eaglepoint.exam.imports.model.ImportJobRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA repository for {@link ImportJobRow}.
 */
@Repository
public interface ImportJobRowRepository extends JpaRepository<ImportJobRow, Long> {

    List<ImportJobRow> findByImportJobId(Long importJobId);

    List<ImportJobRow> findByImportJobIdAndIsValidTrue(Long importJobId);

    List<ImportJobRow> findByImportJobIdAndIsValidFalse(Long importJobId);
}
