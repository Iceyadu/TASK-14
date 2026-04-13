package com.eaglepoint.exam.scheduling.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.compliance.service.ComplianceReviewService;
import com.eaglepoint.exam.versioning.service.VersionService;
import com.eaglepoint.exam.scheduling.dto.CreateExamSessionRequest;
import com.eaglepoint.exam.scheduling.dto.ExamSessionResponse;
import com.eaglepoint.exam.scheduling.model.ExamSession;
import com.eaglepoint.exam.scheduling.model.ExamSessionClass;
import com.eaglepoint.exam.scheduling.model.ExamSessionStatus;
import com.eaglepoint.exam.roster.model.RosterEntry;
import com.eaglepoint.exam.scheduling.repository.ExamSessionClassRepository;
import com.eaglepoint.exam.scheduling.repository.ExamSessionRepository;
import com.eaglepoint.exam.security.service.IdempotencyService;
import com.eaglepoint.exam.security.service.ScopeService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.enums.ScopeType;
import com.eaglepoint.exam.shared.exception.AccessDeniedException;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import com.eaglepoint.exam.shared.exception.StateTransitionException;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service managing exam session lifecycle including creation, status transitions,
 * scope enforcement, and auditing.
 */
@Service
public class ExamSessionService {

    private final ExamSessionRepository examSessionRepository;
    private final ExamSessionClassRepository examSessionClassRepository;
    private final ExamSessionStateMachine stateMachine;
    private final ScopeService scopeService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ComplianceReviewService complianceReviewService;
    private final VersionService versionService;

    public ExamSessionService(ExamSessionRepository examSessionRepository,
                              ExamSessionClassRepository examSessionClassRepository,
                              ExamSessionStateMachine stateMachine,
                              ScopeService scopeService,
                              IdempotencyService idempotencyService,
                              AuditService auditService,
                              ComplianceReviewService complianceReviewService,
                              VersionService versionService) {
        this.examSessionRepository = examSessionRepository;
        this.examSessionClassRepository = examSessionClassRepository;
        this.stateMachine = stateMachine;
        this.scopeService = scopeService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.complianceReviewService = complianceReviewService;
        this.versionService = versionService;
    }

    /**
     * Lists exam sessions with optional filters, applying scope restrictions.
     */
    @Transactional(readOnly = true)
    public Page<ExamSessionResponse> listSessions(Long termId, ExamSessionStatus status,
                                                   Long campusId, Pageable pageable) {
        Long userId = RequestContext.getUserId();
        Role role = RequestContext.getRole();

        if (role == Role.STUDENT) {
            if (status != null && status != ExamSessionStatus.PUBLISHED) {
                return Page.empty(pageable);
            }
        } else if (role != Role.ADMIN) {
            List<Long> campusIds = scopeService.listScopeIds(userId, role, ScopeType.CAMPUS);
            List<Long> termIds = scopeService.listScopeIds(userId, role, ScopeType.TERM);
            List<Long> courseIds = scopeService.listScopeIds(userId, role, ScopeType.COURSE);
            List<Long> classIds = scopeService.listScopeIds(userId, role, ScopeType.CLASS);
            if (campusIds.isEmpty() && termIds.isEmpty() && courseIds.isEmpty() && classIds.isEmpty()) {
                return Page.empty(pageable);
            }
        }

        Specification<ExamSession> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (termId != null) {
                predicates.add(cb.equal(root.get("termId"), termId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (campusId != null) {
                predicates.add(cb.equal(root.get("campusId"), campusId));
            }

            if (role == Role.STUDENT) {
                predicates.add(cb.equal(root.get("status"), ExamSessionStatus.PUBLISHED));
                Subquery<Integer> sub = query.subquery(Integer.class);
                Root<ExamSessionClass> esc = sub.from(ExamSessionClass.class);
                Root<RosterEntry> re = sub.from(RosterEntry.class);
                sub.select(cb.literal(1));
                sub.where(
                        cb.equal(esc.get("examSessionId"), root.get("id")),
                        cb.equal(re.get("classId"), esc.get("classId")),
                        cb.equal(re.get("termId"), root.get("termId")),
                        cb.equal(re.get("studentUserId"), userId),
                        cb.isFalse(re.get("isDeleted"))
                );
                predicates.add(cb.exists(sub));
            } else if (role != Role.ADMIN) {
                List<Long> campusIds = scopeService.listScopeIds(userId, role, ScopeType.CAMPUS);
                List<Long> termIds = scopeService.listScopeIds(userId, role, ScopeType.TERM);
                List<Long> courseIds = scopeService.listScopeIds(userId, role, ScopeType.COURSE);
                List<Long> classIds = scopeService.listScopeIds(userId, role, ScopeType.CLASS);

                List<Predicate> scopeOr = new ArrayList<>();
                if (!campusIds.isEmpty()) {
                    scopeOr.add(root.get("campusId").in(campusIds));
                }
                if (!termIds.isEmpty()) {
                    scopeOr.add(root.get("termId").in(termIds));
                }
                if (!courseIds.isEmpty()) {
                    scopeOr.add(root.get("courseId").in(courseIds));
                }
                if (!classIds.isEmpty()) {
                    Subquery<Integer> clsSub = query.subquery(Integer.class);
                    Root<ExamSessionClass> esc = clsSub.from(ExamSessionClass.class);
                    clsSub.select(cb.literal(1));
                    clsSub.where(
                            cb.equal(esc.get("examSessionId"), root.get("id")),
                            esc.get("classId").in(classIds));
                    scopeOr.add(cb.exists(clsSub));
                }
                predicates.add(cb.or(scopeOr.toArray(new Predicate[0])));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ExamSession> page = examSessionRepository.findAll(spec, pageable);
        return page.map(this::toResponse);
    }

    /**
     * Gets a single exam session by ID, enforcing scope access.
     */
    @Transactional(readOnly = true)
    public ExamSessionResponse getSession(Long id) {
        ExamSession session = findSessionOrThrow(id);
        enforceSessionScope(session);
        return toResponse(session);
    }

    /**
     * Creates a new exam session in DRAFT status.
     */
    @Transactional
    public ExamSessionResponse createSession(CreateExamSessionRequest request) {
        Long userId = RequestContext.getUserId();
        enforceRequestScope(request);

        ExamSession session = new ExamSession();
        session.setName(request.getName());
        session.setTermId(request.getTermId());
        session.setCourseId(request.getCourseId());
        session.setCampusId(request.getCampusId());
        session.setScheduledDate(request.getExamDate());
        session.setStartTime(request.getStartTime());
        session.setEndTime(request.getEndTime());
        session.setRoomId(request.getRoomId());
        session.setStatus(ExamSessionStatus.DRAFT);
        session.setCreatedBy(userId);

        ExamSession saved = examSessionRepository.save(session);

        // Save class associations
        saveClassAssociations(saved.getId(), request.getClassIds());

        auditService.logAction("CREATE_SESSION", "ExamSession", saved.getId(),
                null, saved.getStatus().name(), "Created exam session: " + saved.getName());

        ExamSessionResponse created = toResponse(saved, request.getClassIds());
        versionService.createVersion("ExamSession", saved.getId(), created);
        return created;
    }

    /**
     * Updates an existing exam session. Only allowed when status is DRAFT or REJECTED.
     */
    @Transactional
    public ExamSessionResponse updateSession(Long id, CreateExamSessionRequest request) {
        ExamSession session = findSessionOrThrow(id);
        enforceSessionScope(session);
        enforceRequestScope(request);

        String oldStatus = session.getStatus().name();

        if (session.getStatus() != ExamSessionStatus.DRAFT
                && session.getStatus() != ExamSessionStatus.REJECTED) {
            throw new StateTransitionException(session.getStatus().name(), "EDIT",
                    "Session can only be edited in DRAFT or REJECTED status");
        }

        // If session was REJECTED, move back to DRAFT for re-review
        if (session.getStatus() == ExamSessionStatus.REJECTED) {
            session.setStatus(ExamSessionStatus.DRAFT);
        }

        session.setName(request.getName());
        session.setTermId(request.getTermId());
        session.setCourseId(request.getCourseId());
        session.setCampusId(request.getCampusId());
        session.setScheduledDate(request.getExamDate());
        session.setStartTime(request.getStartTime());
        session.setEndTime(request.getEndTime());
        session.setRoomId(request.getRoomId());

        ExamSession saved = examSessionRepository.save(session);

        // Replace class associations
        examSessionClassRepository.deleteByExamSessionId(id);
        saveClassAssociations(id, request.getClassIds());

        auditService.logAction("UPDATE_SESSION", "ExamSession", saved.getId(),
                oldStatus, saved.getStatus().name(), "Updated exam session: " + saved.getName());

        ExamSessionResponse updated = toResponse(saved, request.getClassIds());
        versionService.createVersion("ExamSession", saved.getId(), updated);
        return updated;
    }

    /**
     * Submits a DRAFT session for compliance review.
     */
    @Transactional
    public ExamSessionResponse submitForReview(Long id) {
        ExamSession session = findSessionOrThrow(id);
        enforceSessionScope(session);

        stateMachine.validateTransition(session.getStatus(), ExamSessionStatus.SUBMITTED_FOR_COMPLIANCE_REVIEW);

        String oldStatus = session.getStatus().name();
        session.setStatus(ExamSessionStatus.SUBMITTED_FOR_COMPLIANCE_REVIEW);
        ExamSession saved = examSessionRepository.save(session);

        complianceReviewService.createReview("ExamSession", saved.getId());

        auditService.logAction("SUBMIT_FOR_REVIEW", "ExamSession", saved.getId(),
                oldStatus, saved.getStatus().name(), "Submitted session for compliance review");

        return toResponse(saved);
    }

    /**
     * Publishes an APPROVED session with idempotency check.
     */
    @Transactional
    public ExamSessionResponse publishSession(Long id, String idempotencyKey) {
        Long userId = RequestContext.getUserId();

        // Check idempotency
        if (idempotencyKey != null) {
            Object existing = idempotencyService.checkAndStore(idempotencyKey, userId, "PUBLISH_SESSION");
            if (existing != null) {
                // Already processed -- return the existing session state
                ExamSession session = findSessionOrThrow(id);
                return toResponse(session);
            }
        }

        ExamSession session = findSessionOrThrow(id);
        enforceSessionScope(session);

        boolean approved = complianceReviewService.isApproved("ExamSession", id);
        if (!approved) {
            throw new StateTransitionException(session.getStatus().name(), ExamSessionStatus.PUBLISHED.name(),
                    "Exam session must be compliance-approved before publishing");
        }

        stateMachine.validateTransition(session.getStatus(), ExamSessionStatus.PUBLISHED);

        String oldStatus = session.getStatus().name();
        session.setStatus(ExamSessionStatus.PUBLISHED);
        ExamSession saved = examSessionRepository.save(session);

        ExamSessionResponse response = toResponse(saved);

        // Store idempotency response
        if (idempotencyKey != null) {
            idempotencyService.storeResponse(idempotencyKey, userId, "PUBLISH_SESSION", response);
        }

        auditService.logAction("PUBLISH_SESSION", "ExamSession", saved.getId(),
                oldStatus, saved.getStatus().name(), "Published exam session");

        versionService.createVersion("ExamSession", saved.getId(), response);

        return response;
    }

    /**
     * Unpublishes a PUBLISHED session.
     */
    @Transactional
    public ExamSessionResponse unpublishSession(Long id) {
        ExamSession session = findSessionOrThrow(id);
        enforceSessionScope(session);

        stateMachine.validateTransition(session.getStatus(), ExamSessionStatus.UNPUBLISHED);

        String oldStatus = session.getStatus().name();
        session.setStatus(ExamSessionStatus.UNPUBLISHED);
        ExamSession saved = examSessionRepository.save(session);

        auditService.logAction("UNPUBLISH_SESSION", "ExamSession", saved.getId(),
                oldStatus, saved.getStatus().name(), "Unpublished exam session");

        return toResponse(saved);
    }

    /**
     * Archives an UNPUBLISHED session.
     */
    @Transactional
    public ExamSessionResponse archiveSession(Long id) {
        ExamSession session = findSessionOrThrow(id);
        enforceSessionScope(session);

        stateMachine.validateTransition(session.getStatus(), ExamSessionStatus.ARCHIVED);

        String oldStatus = session.getStatus().name();
        session.setStatus(ExamSessionStatus.ARCHIVED);
        ExamSession saved = examSessionRepository.save(session);

        auditService.logAction("ARCHIVE_SESSION", "ExamSession", saved.getId(),
                oldStatus, saved.getStatus().name(), "Archived exam session");

        return toResponse(saved);
    }

    /**
     * Returns the exam schedule for a student based on their class enrollments.
     */
    @Transactional(readOnly = true)
    public List<ExamSessionResponse> getStudentSchedule(Long studentUserId) {
        List<ExamSession> sessions = examSessionRepository.findPublishedSessionsForStudent(studentUserId);
        return sessions.stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ---- Private helpers ----

    private ExamSession findSessionOrThrow(Long id) {
        return examSessionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ExamSession", id));
    }

    private List<Long> classIdsForSession(Long examSessionId) {
        return examSessionClassRepository.findByExamSessionId(examSessionId).stream()
                .map(ExamSessionClass::getClassId)
                .collect(Collectors.toList());
    }

    private void enforceSessionScope(ExamSession session) {
        scopeService.enforceExamSessionScope(
                RequestContext.getUserId(),
                RequestContext.getRole(),
                session.getCampusId(),
                session.getTermId(),
                session.getCourseId(),
                classIdsForSession(session.getId()));
    }

    private void enforceRequestScope(CreateExamSessionRequest request) {
        scopeService.enforceExamSessionScope(
                RequestContext.getUserId(),
                RequestContext.getRole(),
                request.getCampusId(),
                request.getTermId(),
                request.getCourseId(),
                request.getClassIds());
    }

    private void saveClassAssociations(Long examSessionId, List<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) {
            return;
        }
        List<ExamSessionClass> associations = classIds.stream()
                .map(classId -> new ExamSessionClass(examSessionId, classId))
                .collect(Collectors.toList());
        examSessionClassRepository.saveAll(associations);
    }

    private ExamSessionResponse toResponse(ExamSession session) {
        List<ExamSessionClass> classes = examSessionClassRepository.findByExamSessionId(session.getId());
        List<Long> classIds = classes.stream()
                .map(ExamSessionClass::getClassId)
                .collect(Collectors.toList());
        return toResponse(session, classIds);
    }

    private ExamSessionResponse toResponse(ExamSession session, List<Long> classIds) {
        ExamSessionResponse response = new ExamSessionResponse();
        response.setId(session.getId());
        response.setName(session.getName());
        response.setTermId(session.getTermId());
        response.setCourseId(session.getCourseId());
        response.setCampusId(session.getCampusId());
        response.setRoomId(session.getRoomId());
        response.setExamDate(session.getScheduledDate());
        response.setStartTime(session.getStartTime());
        response.setEndTime(session.getEndTime());
        response.setStatus(session.getStatus());
        response.setCreatedBy(session.getCreatedBy());
        response.setClassIds(classIds);
        response.setCreatedAt(session.getCreatedAt());
        response.setUpdatedAt(session.getUpdatedAt());
        return response;
    }
}
