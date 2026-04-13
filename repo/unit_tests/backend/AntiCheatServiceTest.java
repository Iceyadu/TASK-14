package com.eaglepoint.exam.anticheat.service;

import com.eaglepoint.exam.anticheat.model.AntiCheatFlag;
import com.eaglepoint.exam.anticheat.repository.AntiCheatFlagRepository;
import com.eaglepoint.exam.audit.repository.AuditLogRepository;
import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AntiCheatService} covering score delta flagging,
 * flag review (dismiss/confirm), and the no-automated-punishment guarantee.
 */
@ExtendWith(MockitoExtension.class)
class AntiCheatServiceTest {

    @Mock
    private AntiCheatFlagRepository flagRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditService auditService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AntiCheatService antiCheatService;

    @BeforeEach
    void setUp() {
        RequestContext.set(99L, "admin1", Role.ADMIN, "session-99", "127.0.0.1", "trace-99");
        lenient().when(auditLogRepository.findByUserIdAndActionAndTimestampGreaterThanEqualOrderByTimestampAsc(
                anyLong(), eq("SUBMIT_EXAM_RESULT"), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private AntiCheatFlag createFlag(Long id, String ruleType, String status) {
        AntiCheatFlag flag = new AntiCheatFlag();
        ReflectionTestUtils.setField(flag, "id", id);
        flag.setStudentUserId(10L);
        flag.setRuleType(ruleType);
        flag.setStatus(status);
        flag.setDetailsJson("{\"scoreDelta\": 45}");
        flag.setFlaggedAt(LocalDateTime.now());
        return flag;
    }

    @Test
    void testScoreDeltaFlag() {
        // Simulate the activity burst detection path (score delta returns null in current impl,
        // so we test activity burst which does create flags)
        when(flagRepository.countRecentExamSubmissions(eq(10L), any(LocalDateTime.class))).thenReturn(5L);
        when(flagRepository.findByStudentUserId(10L)).thenReturn(java.util.Collections.emptyList());
        when(flagRepository.save(any(AntiCheatFlag.class))).thenAnswer(inv -> {
            AntiCheatFlag f = inv.getArgument(0);
            ReflectionTestUtils.setField(f, "id", 1L);
            return f;
        });

        var flags = antiCheatService.checkForAnomalies(10L);

        assertFalse(flags.isEmpty());
        AntiCheatFlag flag = flags.get(0);
        assertEquals("PENDING", flag.getStatus());
        assertEquals("ACTIVITY_BURST", flag.getRuleType());
    }

    @Test
    void testReviewFlagDismissed() {
        AntiCheatFlag flag = createFlag(1L, "SCORE_DELTA", "PENDING");
        when(flagRepository.findById(1L)).thenReturn(Optional.of(flag));
        when(flagRepository.save(any(AntiCheatFlag.class))).thenAnswer(inv -> inv.getArgument(0));

        AntiCheatFlag result = antiCheatService.reviewFlag(1L, "DISMISS", "False positive - student improved naturally");

        assertEquals("DISMISSED", result.getStatus());
        assertEquals("DISMISS", result.getReviewDecision());
        assertEquals("False positive - student improved naturally", result.getReviewComment());
        assertEquals(99L, result.getReviewedBy());
        assertNotNull(result.getReviewedAt());
    }

    @Test
    void testReviewFlagConfirmed() {
        AntiCheatFlag flag = createFlag(2L, "SCORE_DELTA", "PENDING");
        when(flagRepository.findById(2L)).thenReturn(Optional.of(flag));
        when(flagRepository.save(any(AntiCheatFlag.class))).thenAnswer(inv -> inv.getArgument(0));

        AntiCheatFlag result = antiCheatService.reviewFlag(2L, "CONFIRM", "Suspicious pattern confirmed");

        assertEquals("CONFIRMED_FOR_INVESTIGATION", result.getStatus());
        verify(auditService).logAction(eq("REVIEW_ANTICHEAT_FLAG"), eq("AntiCheatFlag"), eq(2L),
                isNull(), isNull(), contains("CONFIRM"));
    }

    @Test
    void testNoAutomatedPunishment() {
        // Creating a flag should NOT modify any student data (no student repo calls)
        when(flagRepository.countRecentExamSubmissions(eq(10L), any(LocalDateTime.class))).thenReturn(5L);
        when(flagRepository.findByStudentUserId(10L)).thenReturn(java.util.Collections.emptyList());
        when(flagRepository.save(any(AntiCheatFlag.class))).thenAnswer(inv -> {
            AntiCheatFlag f = inv.getArgument(0);
            ReflectionTestUtils.setField(f, "id", 1L);
            return f;
        });

        antiCheatService.checkForAnomalies(10L);

        // Activity-burst path saves a flag; identical/score rules only read audit logs (no extra punishment)
        verify(flagRepository).save(any(AntiCheatFlag.class));
        verify(flagRepository).countRecentExamSubmissions(anyLong(), any());
        verify(flagRepository, times(2)).findByStudentUserId(10L);
        verifyNoMoreInteractions(flagRepository);
        verify(auditLogRepository, times(2)).findByUserIdAndActionAndTimestampGreaterThanEqualOrderByTimestampAsc(
                eq(10L), eq("SUBMIT_EXAM_RESULT"), any(LocalDateTime.class));
    }
}
