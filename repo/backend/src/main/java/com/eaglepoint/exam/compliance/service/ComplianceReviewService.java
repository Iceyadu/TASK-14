package com.eaglepoint.exam.compliance.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.compliance.model.ComplianceReview;
import com.eaglepoint.exam.compliance.model.ComplianceReviewStatus;
import com.eaglepoint.exam.compliance.repository.ComplianceReviewRepository;
import com.eaglepoint.exam.scheduling.model.ExamSession;
import com.eaglepoint.exam.scheduling.model.ExamSessionStatus;
import com.eaglepoint.exam.scheduling.repository.ExamSessionRepository;
import com.eaglepoint.exam.scheduling.service.ExamSessionStateMachine;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import com.eaglepoint.exam.shared.exception.StateTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service managing compliance reviews for entities (notifications, exam sessions, etc.).
 */
@Service
public class ComplianceReviewService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceReviewService.class);

    private final ComplianceReviewRepository reviewRepository;
    private final AuditService auditService;
    private final ExamSessionRepository examSessionRepository;
    private final ExamSessionStateMachine examSessionStateMachine;
    private final ContentSafeguardService contentSafeguardService;

    public ComplianceReviewService(ComplianceReviewRepository reviewRepository,
                                   AuditService auditService,
                                   ExamSessionRepository examSessionRepository,
                                   ExamSessionStateMachine examSessionStateMachine,
                                   ContentSafeguardService contentSafeguardService) {
        this.reviewRepository = reviewRepository;
        this.auditService = auditService;
        this.examSessionRepository = examSessionRepository;
        this.examSessionStateMachine = examSessionStateMachine;
        this.contentSafeguardService = contentSafeguardService;
    }

    /**
     * Creates a new PENDING compliance review for the specified entity.
     * Runs automated content safeguard screening before creating the review.
     * If safeguards flag the content, the review is still created but marked
     * with safeguard violations for the reviewer's attention.
     */
    @Transactional
    public ComplianceReview createReview(String entityType, Long entityId) {
        Long userId = RequestContext.getUserId();

        // Run automated content safeguard screening
        ContentSafeguardService.ScreeningResult screening =
                contentSafeguardService.screenContent(
                        resolveContentForScreening(entityType, entityId),
                        entityType, entityId);

        ComplianceReview review = new ComplianceReview();
        review.setEntityType(entityType);
        review.setEntityId(entityId);
        review.setStatus(ComplianceReviewStatus.PENDING);
        review.setSubmittedBy(userId);

        if (!screening.isPassed()) {
            review.setRequiredChanges(
                    "AUTOMATED SAFEGUARD FLAGS: " + String.join("; ", screening.getViolations()));
        }

        ComplianceReview saved = reviewRepository.save(review);

        auditService.logAction("CREATE_COMPLIANCE_REVIEW", entityType, entityId,
                null, null,
                "Created compliance review #" + saved.getId() + " for " + entityType + "#" + entityId);

        log.info("Created compliance review #{} for {}#{}", saved.getId(), entityType, entityId);
        return saved;
    }

    /**
     * Lists pending compliance reviews, paginated.
     */
    @Transactional(readOnly = true)
    public Page<ComplianceReview> listPendingReviews(Pageable pageable) {
        return reviewRepository.findByStatus(ComplianceReviewStatus.PENDING, pageable);
    }

    /**
     * Returns a single compliance review by ID.
     */
    @Transactional(readOnly = true)
    public ComplianceReview getReview(Long id) {
        return findOrThrow(id);
    }

    /**
     * Approves a PENDING compliance review.
     */
    @Transactional
    public ComplianceReview approve(Long id, String comment) {
        ComplianceReview review = findOrThrow(id);

        if (review.getStatus() != ComplianceReviewStatus.PENDING) {
            throw new StateTransitionException(
                    review.getStatus().name(), ComplianceReviewStatus.APPROVED.name(),
                    "Only PENDING reviews can be approved");
        }

        Long reviewerId = RequestContext.getUserId();
        review.setStatus(ComplianceReviewStatus.APPROVED);
        review.setReviewedBy(reviewerId);
        review.setComment(comment);
        review.setReviewedAt(LocalDateTime.now());

        ComplianceReview saved = reviewRepository.save(review);

        applyExamSessionOutcomeAfterComplianceDecision(saved, true);

        auditService.logAction("APPROVE_COMPLIANCE_REVIEW", review.getEntityType(), review.getEntityId(),
                null, null,
                "Approved compliance review #" + id + ": " + comment);

        return saved;
    }

    /**
     * Rejects a PENDING compliance review, optionally specifying required changes.
     */
    @Transactional
    public ComplianceReview reject(Long id, String comment, String requiredChanges) {
        ComplianceReview review = findOrThrow(id);

        if (review.getStatus() != ComplianceReviewStatus.PENDING) {
            throw new StateTransitionException(
                    review.getStatus().name(), ComplianceReviewStatus.REJECTED.name(),
                    "Only PENDING reviews can be rejected");
        }

        Long reviewerId = RequestContext.getUserId();

        ComplianceReviewStatus targetStatus = (requiredChanges != null && !requiredChanges.isBlank())
                ? ComplianceReviewStatus.REQUIRES_CHANGES
                : ComplianceReviewStatus.REJECTED;

        review.setStatus(targetStatus);
        review.setReviewedBy(reviewerId);
        review.setComment(comment);
        review.setRequiredChanges(requiredChanges);
        review.setReviewedAt(LocalDateTime.now());

        ComplianceReview saved = reviewRepository.save(review);

        applyExamSessionOutcomeAfterComplianceDecision(saved, false);

        auditService.logAction("REJECT_COMPLIANCE_REVIEW", review.getEntityType(), review.getEntityId(),
                null, null,
                "Rejected compliance review #" + id + " (" + targetStatus + "): " + comment);

        return saved;
    }

    private void applyExamSessionOutcomeAfterComplianceDecision(ComplianceReview review, boolean approved) {
        if (!"ExamSession".equals(review.getEntityType())) {
            return;
        }
        ExamSession session = examSessionRepository.findById(review.getEntityId())
                .orElseThrow(() -> new EntityNotFoundException("ExamSession", review.getEntityId()));
        if (session.getStatus() != ExamSessionStatus.SUBMITTED_FOR_COMPLIANCE_REVIEW) {
            throw new StateTransitionException(
                    session.getStatus().name(),
                    approved ? ExamSessionStatus.APPROVED.name() : ExamSessionStatus.REJECTED.name(),
                    "Exam session must be awaiting compliance review");
        }
        if (approved) {
            examSessionStateMachine.validateTransition(session.getStatus(), ExamSessionStatus.APPROVED);
            session.setStatus(ExamSessionStatus.APPROVED);
        } else {
            examSessionStateMachine.validateTransition(session.getStatus(), ExamSessionStatus.REJECTED);
            session.setStatus(ExamSessionStatus.REJECTED);
        }
        examSessionRepository.save(session);
    }

    /**
     * Returns true if the latest compliance review for the given entity is APPROVED.
     */
    @Transactional(readOnly = true)
    public boolean isApproved(String entityType, Long entityId) {
        Optional<ComplianceReview> latest = reviewRepository
                .findTopByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
        return latest.isPresent() && latest.get().getStatus() == ComplianceReviewStatus.APPROVED;
    }

    /**
     * Returns true if the entity has a pending or approved compliance review.
     */
    @Transactional(readOnly = true)
    public boolean requiresReview(String entityType, Long entityId) {
        List<ComplianceReview> reviews = reviewRepository.findByEntityTypeAndEntityId(entityType, entityId);
        return reviews.stream().anyMatch(r ->
                r.getStatus() == ComplianceReviewStatus.PENDING
                        || r.getStatus() == ComplianceReviewStatus.APPROVED);
    }

    // ---- Private helpers ----

    /**
     * Resolves the text content to screen for the given entity.
     */
    private String resolveContentForScreening(String entityType, Long entityId) {
        if ("ExamSession".equals(entityType)) {
            return examSessionRepository.findById(entityId)
                    .map(ExamSession::getName)
                    .orElse("");
        }
        // For other entity types, return empty (screening is a no-op)
        return "";
    }

    private ComplianceReview findOrThrow(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ComplianceReview", id));
    }
}
