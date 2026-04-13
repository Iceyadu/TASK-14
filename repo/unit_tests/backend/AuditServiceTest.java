package com.eaglepoint.exam.audit.service;

import com.eaglepoint.exam.audit.model.AuditLog;
import com.eaglepoint.exam.audit.repository.AuditLogRepository;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuditService} covering audit log creation,
 * request context capture, and filtered queries.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        RequestContext.set(42L, "coordinator1", Role.ACADEMIC_COORDINATOR,
                "session-abc", "192.168.1.100", "trace-xyz-123");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void testAuditCreated() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.logAction("CREATE_SESSION", "ExamSession", 1L,
                null, "{\"status\":\"DRAFT\"}", "Created exam session: Midterm");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("CREATE_SESSION", saved.getAction());
        assertEquals("ExamSession", saved.getEntityType());
        assertEquals(1L, saved.getEntityId());
        assertNull(saved.getOldState());
        assertEquals("{\"status\":\"DRAFT\"}", saved.getNewState());
        assertEquals("Created exam session: Midterm", saved.getDetailsJson());
    }

    @Test
    void testAuditCapturesRequestContext() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditService.logAction("LOGIN", "Session", 100L, null, null, "User logged in");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals(42L, saved.getUserId());
        assertEquals("session-abc", saved.getSessionId());
        assertEquals("192.168.1.100", saved.getIpAddress());
        assertEquals("trace-xyz-123", saved.getTraceId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAuditQueryWithFilters() {
        AuditLog entry1 = new AuditLog();
        entry1.setAction("CREATE_SESSION");
        entry1.setEntityType("ExamSession");

        Page<AuditLog> page = new PageImpl<>(List.of(entry1));
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        LocalDateTime from = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 4, 30, 23, 59);

        Page<AuditLog> result = auditService.queryAuditLog(
                null, "ExamSession", null, from, to, PageRequest.of(0, 20));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("ExamSession", result.getContent().get(0).getEntityType());
    }
}
