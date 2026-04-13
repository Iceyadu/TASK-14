package com.eaglepoint.exam.proctors.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.proctors.dto.CreateProctorAssignmentRequest;
import com.eaglepoint.exam.proctors.model.ProctorAssignment;
import com.eaglepoint.exam.proctors.repository.ProctorAssignmentRepository;
import com.eaglepoint.exam.scheduling.model.ExamSession;
import com.eaglepoint.exam.scheduling.model.ExamSessionClass;
import com.eaglepoint.exam.scheduling.repository.ExamSessionClassRepository;
import com.eaglepoint.exam.scheduling.repository.ExamSessionRepository;
import com.eaglepoint.exam.security.service.ScopeService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service managing proctor assignments to exam session rooms with scope enforcement.
 */
@Service
public class ProctorService {

    private final ProctorAssignmentRepository proctorAssignmentRepository;
    private final ExamSessionRepository examSessionRepository;
    private final ExamSessionClassRepository examSessionClassRepository;
    private final ScopeService scopeService;
    private final AuditService auditService;

    public ProctorService(ProctorAssignmentRepository proctorAssignmentRepository,
                          ExamSessionRepository examSessionRepository,
                          ExamSessionClassRepository examSessionClassRepository,
                          ScopeService scopeService,
                          AuditService auditService) {
        this.proctorAssignmentRepository = proctorAssignmentRepository;
        this.examSessionRepository = examSessionRepository;
        this.examSessionClassRepository = examSessionClassRepository;
        this.scopeService = scopeService;
        this.auditService = auditService;
    }

    /**
     * Lists all proctor assignments for a given exam session.
     */
    @Transactional(readOnly = true)
    public List<ProctorAssignment> listAssignments(Long examSessionId) {
        ExamSession session = examSessionRepository.findById(examSessionId)
                .orElseThrow(() -> new EntityNotFoundException("ExamSession", examSessionId));

        enforceExamSessionScope(session);

        return proctorAssignmentRepository.findByExamSessionId(examSessionId);
    }

    /**
     * Creates a new proctor assignment with scope enforcement.
     */
    @Transactional
    public ProctorAssignment createAssignment(CreateProctorAssignmentRequest request) {
        ExamSession session = examSessionRepository.findById(request.getExamSessionId())
                .orElseThrow(() -> new EntityNotFoundException("ExamSession", request.getExamSessionId()));

        enforceExamSessionScope(session);

        ProctorAssignment assignment = new ProctorAssignment();
        assignment.setUserId(request.getUserId());
        assignment.setExamSessionId(request.getExamSessionId());
        assignment.setRoomId(request.getRoomId());

        ProctorAssignment saved = proctorAssignmentRepository.save(assignment);

        auditService.logAction("CREATE_PROCTOR_ASSIGNMENT", "ProctorAssignment", saved.getId(),
                null, null,
                "Assigned user " + saved.getUserId() + " as proctor for session " + saved.getExamSessionId());

        return saved;
    }

    /**
     * Deletes a proctor assignment with scope enforcement.
     */
    @Transactional
    public void deleteAssignment(Long id) {
        ProctorAssignment assignment = proctorAssignmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ProctorAssignment", id));

        ExamSession session = examSessionRepository.findById(assignment.getExamSessionId())
                .orElseThrow(() -> new EntityNotFoundException("ExamSession", assignment.getExamSessionId()));

        enforceExamSessionScope(session);

        proctorAssignmentRepository.delete(assignment);

        auditService.logAction("DELETE_PROCTOR_ASSIGNMENT", "ProctorAssignment", id,
                null, null,
                "Removed proctor assignment for user " + assignment.getUserId()
                        + " from session " + assignment.getExamSessionId());
    }

    private void enforceExamSessionScope(ExamSession session) {
        List<Long> classIds = examSessionClassRepository.findByExamSessionId(session.getId()).stream()
                .map(ExamSessionClass::getClassId)
                .collect(Collectors.toList());
        scopeService.enforceExamSessionScope(
                RequestContext.getUserId(),
                RequestContext.getRole(),
                session.getCampusId(),
                session.getTermId(),
                session.getCourseId(),
                classIds);
    }
}
