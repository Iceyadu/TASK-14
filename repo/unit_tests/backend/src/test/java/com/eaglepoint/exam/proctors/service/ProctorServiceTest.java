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
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProctorServiceTest {

    @Mock private ProctorAssignmentRepository proctorAssignmentRepository;
    @Mock private ExamSessionRepository examSessionRepository;
    @Mock private ExamSessionClassRepository examSessionClassRepository;
    @Mock private ScopeService scopeService;
    @Mock private AuditService auditService;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private ProctorService newService() {
        return new ProctorService(proctorAssignmentRepository, examSessionRepository,
                examSessionClassRepository, scopeService, auditService);
    }

    private ExamSession sampleSession(Long id, Long campusId, Long termId, Long courseId) {
        ExamSession session = new ExamSession();
        session.setId(id);
        session.setCampusId(campusId);
        session.setTermId(termId);
        session.setCourseId(courseId);
        return session;
    }

    private ExamSessionClass sampleEsc(Long classId) {
        ExamSessionClass esc = new ExamSessionClass();
        esc.setClassId(classId);
        return esc;
    }

    // ---- createAssignment ----

    @Test
    void testCreateAssignmentEnforcesExamSessionScope() {
        RequestContext.set(5L, "coord", Role.ACADEMIC_COORDINATOR, "s", "127.0.0.1", "t");

        ExamSession session = sampleSession(11L, 2L, 3L, 4L);
        when(examSessionRepository.findById(11L)).thenReturn(Optional.of(session));
        when(examSessionClassRepository.findByExamSessionId(11L)).thenReturn(List.of(sampleEsc(99L)));
        when(proctorAssignmentRepository.save(any(ProctorAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateProctorAssignmentRequest req = new CreateProctorAssignmentRequest();
        req.setExamSessionId(11L);
        req.setRoomId(21L);
        req.setUserId(31L);

        newService().createAssignment(req);

        verify(scopeService).enforceExamSessionScope(5L, Role.ACADEMIC_COORDINATOR, 2L, 3L, 4L, List.of(99L));
        verify(proctorAssignmentRepository).save(any(ProctorAssignment.class));
        verify(auditService).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testCreateAssignmentThrowsForMissingSession() {
        RequestContext.set(5L, "coord", Role.ACADEMIC_COORDINATOR, "s", "127.0.0.1", "t");
        when(examSessionRepository.findById(999L)).thenReturn(Optional.empty());

        CreateProctorAssignmentRequest req = new CreateProctorAssignmentRequest();
        req.setExamSessionId(999L);
        req.setRoomId(1L);
        req.setUserId(1L);

        assertThrows(EntityNotFoundException.class, () -> newService().createAssignment(req));
    }

    // ---- deleteAssignment ----

    @Test
    void testDeleteAssignmentEnforcesSessionScopeAndDeletes() {
        RequestContext.set(5L, "coord", Role.ACADEMIC_COORDINATOR, "s", "127.0.0.1", "t");

        ProctorAssignment assignment = new ProctorAssignment();
        assignment.setId(100L);
        assignment.setExamSessionId(11L);
        assignment.setUserId(31L);
        assignment.setRoomId(21L);

        when(proctorAssignmentRepository.findById(100L)).thenReturn(Optional.of(assignment));
        ExamSession session = sampleSession(11L, 2L, 3L, 4L);
        when(examSessionRepository.findById(11L)).thenReturn(Optional.of(session));
        when(examSessionClassRepository.findByExamSessionId(11L)).thenReturn(List.of(sampleEsc(99L)));

        newService().deleteAssignment(100L);

        verify(proctorAssignmentRepository).delete(assignment);
        verify(auditService).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testDeleteAssignmentThrowsForMissingAssignment() {
        RequestContext.set(5L, "coord", Role.ACADEMIC_COORDINATOR, "s", "127.0.0.1", "t");
        when(proctorAssignmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> newService().deleteAssignment(999L));
    }

    // ---- listAssignments ----

    @Test
    void testListAssignmentsReturnsAssignmentsForSession() {
        RequestContext.set(1L, "admin", Role.ADMIN, "s", "127.0.0.1", "t");

        ExamSession session = sampleSession(11L, 2L, 3L, 4L);
        when(examSessionRepository.findById(11L)).thenReturn(Optional.of(session));
        when(examSessionClassRepository.findByExamSessionId(11L)).thenReturn(List.of());

        ProctorAssignment pa = new ProctorAssignment();
        pa.setId(1L);
        pa.setExamSessionId(11L);
        pa.setUserId(31L);
        pa.setRoomId(21L);
        when(proctorAssignmentRepository.findByExamSessionId(11L)).thenReturn(List.of(pa));

        List<ProctorAssignment> result = newService().listAssignments(11L);

        assertEquals(1, result.size());
        assertEquals(11L, result.get(0).getExamSessionId());
    }

    @Test
    void testListAssignmentsThrowsForMissingSession() {
        RequestContext.set(1L, "admin", Role.ADMIN, "s", "127.0.0.1", "t");
        when(examSessionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> newService().listAssignments(999L));
    }
}
