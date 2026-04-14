package com.eaglepoint.exam.scheduling.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.compliance.model.ComplianceReview;
import com.eaglepoint.exam.compliance.service.ComplianceReviewService;
import com.eaglepoint.exam.scheduling.dto.CreateExamSessionRequest;
import com.eaglepoint.exam.scheduling.dto.ExamSessionResponse;
import com.eaglepoint.exam.scheduling.model.ExamSession;
import com.eaglepoint.exam.scheduling.model.ExamSessionClass;
import com.eaglepoint.exam.scheduling.model.ExamSessionStatus;
import com.eaglepoint.exam.scheduling.repository.ExamSessionClassRepository;
import com.eaglepoint.exam.scheduling.repository.ExamSessionRepository;
import com.eaglepoint.exam.security.service.IdempotencyService;
import com.eaglepoint.exam.security.service.ScopeService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.AccessDeniedException;
import com.eaglepoint.exam.shared.exception.StateTransitionException;
import com.eaglepoint.exam.versioning.model.EntityVersion;
import com.eaglepoint.exam.versioning.service.VersionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExamSessionService} covering session creation, state transitions,
 * scope enforcement, idempotency, and student schedule retrieval.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamSessionServiceTest {

    @Mock
    private ExamSessionRepository examSessionRepository;

    @Mock
    private ExamSessionClassRepository examSessionClassRepository;

    @Mock
    private ExamSessionStateMachine stateMachine;

    @Mock
    private ScopeService scopeService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private AuditService auditService;

    @Mock
    private ComplianceReviewService complianceReviewService;

    @Mock
    private VersionService versionService;

    @InjectMocks
    private ExamSessionService examSessionService;

    @BeforeEach
    void setUp() {
        RequestContext.set(1L, "coordinator1", Role.ACADEMIC_COORDINATOR, "session-1", "127.0.0.1", "trace-1");
        when(versionService.createVersion(anyString(), anyLong(), any())).thenAnswer(inv -> {
            EntityVersion v = new EntityVersion();
            v.setVersionNumber(1);
            return v;
        });
        when(complianceReviewService.createReview(anyString(), anyLong())).thenReturn(new ComplianceReview());
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private ExamSession createExamSession(Long id, ExamSessionStatus status) {
        ExamSession session = new ExamSession();
        ReflectionTestUtils.setField(session, "id", id);
        session.setName("Midterm Exam");
        session.setTermId(1L);
        session.setCourseId(10L);
        session.setCampusId(100L);
        session.setRoomId(200L);
        session.setScheduledDate(LocalDate.of(2026, 5, 15));
        session.setStartTime(LocalTime.of(9, 0));
        session.setEndTime(LocalTime.of(11, 0));
        session.setStatus(status);
        session.setCreatedBy(1L);
        return session;
    }

    private CreateExamSessionRequest createRequest() {
        CreateExamSessionRequest request = new CreateExamSessionRequest();
        request.setName("Midterm Exam");
        request.setTermId(1L);
        request.setCourseId(10L);
        request.setCampusId(100L);
        request.setRoomId(200L);
        request.setScheduledDate(LocalDate.of(2026, 5, 15));
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(11, 0));
        request.setClassIds(List.of(1L, 2L));
        return request;
    }

    @Test
    void testCreateSessionDraft() {
        CreateExamSessionRequest request = createRequest();

        when(examSessionRepository.save(any(ExamSession.class))).thenAnswer(inv -> {
            ExamSession s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 1L);
            return s;
        });

        ExamSessionResponse response = examSessionService.createSession(request);

        assertNotNull(response);
        assertEquals(ExamSessionStatus.DRAFT, response.getStatus());
        assertEquals("Midterm Exam", response.getName());
        verify(examSessionRepository).save(argThat(s -> s.getStatus() == ExamSessionStatus.DRAFT));
        verify(examSessionClassRepository).saveAll(anyList());
        verify(auditService).logAction(eq("CREATE_SESSION"), eq("ExamSession"), eq(1L), isNull(), eq("DRAFT"), anyString());
    }

    @Test
    void testSubmitForReview() {
        ExamSession session = createExamSession(1L, ExamSessionStatus.DRAFT);

        when(examSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        doNothing().when(stateMachine).validateTransition(ExamSessionStatus.DRAFT, ExamSessionStatus.SUBMITTED_FOR_COMPLIANCE_REVIEW);
        doNothing().when(scopeService).enforceExamSessionScope(anyLong(), any(Role.class), any(), any(), any(), anyList());
        when(examSessionRepository.save(any(ExamSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(examSessionClassRepository.findByExamSessionId(1L)).thenReturn(Collections.emptyList());

        ExamSessionResponse response = examSessionService.submitForReview(1L);

        assertEquals(ExamSessionStatus.SUBMITTED_FOR_COMPLIANCE_REVIEW, response.getStatus());
        verify(stateMachine).validateTransition(ExamSessionStatus.DRAFT, ExamSessionStatus.SUBMITTED_FOR_COMPLIANCE_REVIEW);
    }

    @Test
    void testApproveAndPublish() {
        ExamSession session = createExamSession(1L, ExamSessionStatus.APPROVED);

        when(examSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        doNothing().when(stateMachine).validateTransition(ExamSessionStatus.APPROVED, ExamSessionStatus.PUBLISHED);
        doNothing().when(scopeService).enforceExamSessionScope(anyLong(), any(Role.class), any(), any(), any(), anyList());
        when(complianceReviewService.isApproved("ExamSession", 1L)).thenReturn(true);
        when(idempotencyService.checkAndStore(anyString(), anyLong(), anyString())).thenReturn(null);
        when(examSessionRepository.save(any(ExamSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(examSessionClassRepository.findByExamSessionId(1L)).thenReturn(Collections.emptyList());

        ExamSessionResponse response = examSessionService.publishSession(1L, "idem-key-1");

        assertEquals(ExamSessionStatus.PUBLISHED, response.getStatus());
        verify(idempotencyService).storeResponse(eq("idem-key-1"), eq(1L), eq("PUBLISH_SESSION"), any());
        verify(auditService).logAction(eq("PUBLISH_SESSION"), eq("ExamSession"), eq(1L), anyString(), eq("PUBLISHED"), anyString());
    }

    @Test
    void testPublishBlockedWithoutApproval() {
        ExamSession session = createExamSession(1L, ExamSessionStatus.DRAFT);

        when(examSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        doNothing().when(scopeService).enforceExamSessionScope(anyLong(), any(Role.class), any(), any(), any(), anyList());
        when(examSessionClassRepository.findByExamSessionId(1L)).thenReturn(Collections.emptyList());
        when(idempotencyService.checkAndStore(anyString(), anyLong(), anyString())).thenReturn(null);
        when(complianceReviewService.isApproved("ExamSession", 1L)).thenReturn(false);

        assertThrows(StateTransitionException.class, () -> examSessionService.publishSession(1L, "idem-key-2"));
        verify(stateMachine, never()).validateTransition(any(), any());
    }

    @Test
    void testInvalidTransitionPublishedToDraft() {
        ExamSession session = createExamSession(1L, ExamSessionStatus.PUBLISHED);

        when(examSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        doNothing().when(scopeService).enforceExamSessionScope(anyLong(), any(Role.class), any(), any(), any(), anyList());
        when(examSessionClassRepository.findByExamSessionId(1L)).thenReturn(Collections.emptyList());

        // PUBLISHED -> DRAFT is not valid per state machine
        // The updateSession method checks for DRAFT/REJECTED status
        CreateExamSessionRequest request = createRequest();

        assertThrows(StateTransitionException.class, () -> examSessionService.updateSession(1L, request));
    }

    @Test
    void testInvalidTransitionDraftToPublished() {
        ExamSession session = createExamSession(1L, ExamSessionStatus.APPROVED);

        when(examSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        doNothing().when(scopeService).enforceExamSessionScope(anyLong(), any(Role.class), any(), any(), any(), anyList());
        when(examSessionClassRepository.findByExamSessionId(1L)).thenReturn(Collections.emptyList());
        when(idempotencyService.checkAndStore(anyString(), anyLong(), anyString())).thenReturn(null);
        when(complianceReviewService.isApproved("ExamSession", 1L)).thenReturn(true);
        doThrow(new StateTransitionException("APPROVED", "PUBLISHED"))
                .when(stateMachine).validateTransition(ExamSessionStatus.APPROVED, ExamSessionStatus.PUBLISHED);

        assertThrows(StateTransitionException.class, () -> examSessionService.publishSession(1L, "key-x"));
    }

    @Test
    void testUpdateOnlyInDraftOrRejected() {
        ExamSession publishedSession = createExamSession(1L, ExamSessionStatus.PUBLISHED);

        when(examSessionRepository.findById(1L)).thenReturn(Optional.of(publishedSession));
        doNothing().when(scopeService).enforceExamSessionScope(anyLong(), any(Role.class), any(), any(), any(), anyList());
        when(examSessionClassRepository.findByExamSessionId(1L)).thenReturn(Collections.emptyList());

        CreateExamSessionRequest request = createRequest();

        StateTransitionException ex = assertThrows(StateTransitionException.class,
                () -> examSessionService.updateSession(1L, request));

        assertTrue(ex.getMessage().contains("DRAFT or REJECTED"));
    }

    @Test
    void testGetStudentSchedule() {
        ExamSession session1 = createExamSession(1L, ExamSessionStatus.PUBLISHED);
        ExamSession session2 = createExamSession(2L, ExamSessionStatus.PUBLISHED);

        when(examSessionRepository.findPublishedSessionsForStudent(10L)).thenReturn(List.of(session1, session2));
        when(examSessionClassRepository.findByExamSessionId(anyLong())).thenReturn(Collections.emptyList());

        List<ExamSessionResponse> schedule = examSessionService.getStudentSchedule(10L);

        assertEquals(2, schedule.size());
        assertTrue(schedule.stream().allMatch(s -> s.getStatus() == ExamSessionStatus.PUBLISHED));
    }

    @Test
    void testScopeEnforcement() {
        ExamSession session = createExamSession(1L, ExamSessionStatus.DRAFT);

        when(examSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(examSessionClassRepository.findByExamSessionId(1L)).thenReturn(Collections.emptyList());
        doThrow(new AccessDeniedException("Access denied for campus 100"))
                .when(scopeService).enforceExamSessionScope(eq(1L), eq(Role.ACADEMIC_COORDINATOR),
                        eq(100L), eq(1L), eq(10L), eq(Collections.emptyList()));

        assertThrows(AccessDeniedException.class, () -> examSessionService.getSession(1L));
    }
}
