package com.eaglepoint.exam.compliance.repository;

import com.eaglepoint.exam.compliance.model.ComplianceReview;
import com.eaglepoint.exam.compliance.model.ComplianceReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link ComplianceReview}.
 */
@Repository
public interface ComplianceReviewRepository extends JpaRepository<ComplianceReview, Long> {

    List<ComplianceReview> findByEntityTypeAndEntityId(String entityType, Long entityId);

    Page<ComplianceReview> findByStatus(ComplianceReviewStatus status, Pageable pageable);

    Optional<ComplianceReview> findTopByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, Long entityId);
}
