package com.eaglepoint.exam.versioning.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.compliance.service.ComplianceReviewService;
import com.eaglepoint.exam.notifications.repository.NotificationRepository;
import com.eaglepoint.exam.roster.repository.RosterEntryRepository;
import com.eaglepoint.exam.scheduling.dto.ExamSessionResponse;
import com.eaglepoint.exam.scheduling.model.ExamSession;
import com.eaglepoint.exam.scheduling.model.ExamSessionStatus;
import com.eaglepoint.exam.scheduling.repository.ExamSessionClassRepository;
import com.eaglepoint.exam.scheduling.repository.ExamSessionRepository;
import com.eaglepoint.exam.security.service.IdempotencyService;
import com.eaglepoint.exam.security.service.ScopeService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.versioning.model.EntityVersion;
import com.eaglepoint.exam.versioning.repository.EntityVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VersionService} covering version creation, restore,
 * immutability, comparison, and compliance re-review triggering.
 */
@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

    @Mock
    private EntityVersionRepository versionRepository;

    @Mock
    private ComplianceReviewService complianceReviewService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private AuditService auditService;

    @Mock
    private ExamSessionRepository examSessionRepository;

    @Mock
    private ExamSessionClassRepository examSessionClassRepository;

    @Mock
    private RosterEntryRepository rosterEntryRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ScopeService scopeService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private VersionService versionService;

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        RequestContext.set(1L, "coordinator1", Role.ACADEMIC_COORDINATOR, "session-1", "127.0.0.1", "trace-1");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private EntityVersion createVersion(Long id, String entityType, Long entityId, int versionNumber, String snapshotJson) {
        EntityVersion version = new EntityVersion();
        ReflectionTestUtils.setField(version, "id", id);
        version.setEntityType(entityType);
        version.setEntityId(entityId);
        version.setVersionNumber(versionNumber);
        version.setSnapshotJson(snapshotJson);
        version.setCreatedBy(1L);
        return version;
    }

    private String examSessionSnapshotJson(String name) throws Exception {
        ExamSessionResponse r = new ExamSessionResponse();
        r.setId(1L);
        r.setName(name);
        r.setTermId(1L);
        r.setCourseId(1L);
        r.setCampusId(1L);
        r.setRoomId(1L);
        r.setScheduledDate(LocalDate.of(2025, 1, 15));
        r.setStartTime(LocalTime.of(9, 0));
        r.setEndTime(LocalTime.of(10, 0));
        r.setStatus(ExamSessionStatus.DRAFT);
        r.setCreatedBy(1L);
        r.setClassIds(java.util.List.of());
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        return objectMapper.writeValueAsString(r);
    }

    @Test
    void testCreateVersion() {
        when(versionRepository.countByEntityTypeAndEntityId("ExamSession", 1L)).thenReturn(0L);
        when(versionRepository.save(any(EntityVersion.class))).thenAnswer(inv -> {
            EntityVersion v = inv.getArgument(0);
            ReflectionTestUtils.setField(v, "id", 100L);
            return v;
        });

        Map<String, String> snapshot = Map.of("name", "Midterm", "status", "DRAFT");
        EntityVersion result = versionService.createVersion("ExamSession", 1L, snapshot);

        assertNotNull(result);
        assertEquals(1, result.getVersionNumber());
        assertEquals("ExamSession", result.getEntityType());
        assertEquals(1L, result.getEntityId());
        verify(auditService).logAction(eq("CREATE_VERSION"), eq("ExamSession"), eq(1L), isNull(), isNull(), contains("version 1"));
    }

    @Test
    void testRestoreCreatesNewVersion() throws Exception {
        String snap = examSessionSnapshotJson("Midterm v2");
        EntityVersion v2 = createVersion(2L, "ExamSession", 1L, 2, snap);

        ExamSession session = new ExamSession();
        session.setId(1L);
        session.setCampusId(1L);
        session.setTermId(1L);
        session.setCourseId(1L);
        when(examSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(examSessionClassRepository.findByExamSessionId(1L)).thenReturn(java.util.List.of());

        // Current count is 3 (versions 1, 2, 3 exist)
        when(versionRepository.findByEntityTypeAndEntityIdAndVersionNumber("ExamSession", 1L, 2))
                .thenReturn(Optional.of(v2));
        when(versionRepository.countByEntityTypeAndEntityId("ExamSession", 1L)).thenReturn(3L);
        when(versionRepository.save(any(EntityVersion.class))).thenAnswer(inv -> {
            EntityVersion v = inv.getArgument(0);
            ReflectionTestUtils.setField(v, "id", 200L);
            return v;
        });

        EntityVersion restored = versionService.restoreVersion("ExamSession", 1L, 2, null);

        assertNotNull(restored);
        assertEquals(4, restored.getVersionNumber()); // new version = existing count + 1
        assertEquals(snap, restored.getSnapshotJson());
    }

    @Test
    void testHistoricalVersionsImmutable() throws Exception {
        String snap = examSessionSnapshotJson("v2 data");
        EntityVersion v2 = createVersion(2L, "ExamSession", 1L, 2, snap);

        ExamSession session = new ExamSession();
        session.setId(1L);
        session.setCampusId(1L);
        session.setTermId(1L);
        session.setCourseId(1L);
        when(examSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(examSessionClassRepository.findByExamSessionId(1L)).thenReturn(java.util.List.of());

        when(versionRepository.findByEntityTypeAndEntityIdAndVersionNumber("ExamSession", 1L, 2))
                .thenReturn(Optional.of(v2));
        when(versionRepository.countByEntityTypeAndEntityId("ExamSession", 1L)).thenReturn(3L);
        when(versionRepository.save(any(EntityVersion.class))).thenAnswer(inv -> {
            EntityVersion v = inv.getArgument(0);
            ReflectionTestUtils.setField(v, "id", 300L);
            return v;
        });

        versionService.restoreVersion("ExamSession", 1L, 2, null);

        // The restore creates a NEW version (v4). It should NOT modify any existing version.
        // Verify save is called exactly once (for the new v4), never deletes or modifies existing
        ArgumentCaptor<EntityVersion> captor = ArgumentCaptor.forClass(EntityVersion.class);
        verify(versionRepository).save(captor.capture());
        EntityVersion savedVersion = captor.getValue();
        assertEquals(4, savedVersion.getVersionNumber());
        // The original v2 object should remain unchanged
        assertEquals(2, v2.getVersionNumber());
        assertEquals(snap, v2.getSnapshotJson());
    }

    @Test
    void testCompareVersions() {
        EntityVersion v1 = createVersion(1L, "ExamSession", 1L, 1, "{\"name\":\"v1\"}");
        EntityVersion v2 = createVersion(2L, "ExamSession", 1L, 2, "{\"name\":\"v2\"}");

        when(versionRepository.findByEntityTypeAndEntityIdAndVersionNumber("ExamSession", 1L, 1))
                .thenReturn(Optional.of(v1));
        when(versionRepository.findByEntityTypeAndEntityIdAndVersionNumber("ExamSession", 1L, 2))
                .thenReturn(Optional.of(v2));

        ExamSession session = new ExamSession();
        session.setId(1L);
        session.setCampusId(1L);
        session.setTermId(1L);
        session.setCourseId(1L);
        when(examSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(examSessionClassRepository.findByExamSessionId(1L)).thenReturn(java.util.List.of());

        Map<String, EntityVersion> comparison = versionService.compareVersions("ExamSession", 1L, 1, 2);

        assertNotNull(comparison.get("from"));
        assertNotNull(comparison.get("to"));
        assertEquals("{\"name\":\"v1\"}", comparison.get("from").getSnapshotJson());
        assertEquals("{\"name\":\"v2\"}", comparison.get("to").getSnapshotJson());
    }

    @Test
    void testRestoreTriggersReReview() throws Exception {
        ExamSessionResponse snapObj = new ExamSessionResponse();
        snapObj.setId(5L);
        snapObj.setName("session data");
        snapObj.setTermId(1L);
        snapObj.setCourseId(1L);
        snapObj.setCampusId(1L);
        snapObj.setRoomId(1L);
        snapObj.setScheduledDate(LocalDate.of(2025, 2, 1));
        snapObj.setStartTime(LocalTime.of(9, 0));
        snapObj.setEndTime(LocalTime.of(10, 0));
        snapObj.setStatus(ExamSessionStatus.DRAFT);
        snapObj.setCreatedBy(1L);
        snapObj.setClassIds(java.util.List.of());
        snapObj.setCreatedAt(LocalDateTime.now());
        snapObj.setUpdatedAt(LocalDateTime.now());
        String json = objectMapper.writeValueAsString(snapObj);

        EntityVersion v1 = createVersion(1L, "ExamSession", 5L, 1, json);

        ExamSession session = new ExamSession();
        session.setId(5L);
        session.setCampusId(1L);
        session.setTermId(1L);
        session.setCourseId(1L);
        when(examSessionRepository.findById(5L)).thenReturn(Optional.of(session));
        when(examSessionClassRepository.findByExamSessionId(5L)).thenReturn(java.util.List.of());

        when(versionRepository.findByEntityTypeAndEntityIdAndVersionNumber("ExamSession", 5L, 1))
                .thenReturn(Optional.of(v1));
        when(versionRepository.countByEntityTypeAndEntityId("ExamSession", 5L)).thenReturn(2L);
        when(versionRepository.save(any(EntityVersion.class))).thenAnswer(inv -> {
            EntityVersion v = inv.getArgument(0);
            ReflectionTestUtils.setField(v, "id", 400L);
            return v;
        });

        versionService.restoreVersion("ExamSession", 5L, 1, null);

        // ExamSession is in STUDENT_VISIBLE_ENTITIES, so a compliance review should be triggered
        verify(complianceReviewService).createReview("ExamSession", 5L);
    }

    @Test
    void testVersionNumberSequential() {
        // Simulate sequential creates: count returns 0, then 1, then 2
        when(versionRepository.countByEntityTypeAndEntityId("RosterEntry", 1L))
                .thenReturn(0L).thenReturn(1L).thenReturn(2L);
        when(versionRepository.save(any(EntityVersion.class))).thenAnswer(inv -> {
            EntityVersion v = inv.getArgument(0);
            ReflectionTestUtils.setField(v, "id", (long) v.getVersionNumber());
            return v;
        });

        EntityVersion v1 = versionService.createVersion("RosterEntry", 1L, "snapshot1");
        EntityVersion v2 = versionService.createVersion("RosterEntry", 1L, "snapshot2");
        EntityVersion v3 = versionService.createVersion("RosterEntry", 1L, "snapshot3");

        assertEquals(1, v1.getVersionNumber());
        assertEquals(2, v2.getVersionNumber());
        assertEquals(3, v3.getVersionNumber());
    }
}
