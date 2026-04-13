package com.eaglepoint.exam.roster.repository;

import com.eaglepoint.exam.roster.model.RosterEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA repository for {@link RosterEntry} with specification support for dynamic filtering.
 */
@Repository
public interface RosterEntryRepository extends JpaRepository<RosterEntry, Long>, JpaSpecificationExecutor<RosterEntry> {

    List<RosterEntry> findByClassIdAndTermIdAndIsDeletedFalse(Long classId, Long termId);

    List<RosterEntry> findByClassIdAndIsDeletedFalse(Long classId);

    List<RosterEntry> findByStudentUserIdAndTermId(Long studentUserId, Long termId);
}
