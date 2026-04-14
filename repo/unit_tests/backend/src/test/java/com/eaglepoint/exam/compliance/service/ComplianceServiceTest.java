package com.eaglepoint.exam.compliance.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.compliance.model.ComplianceReview;
import com.eaglepoint.exam.compliance.model.ComplianceReviewStatus;
import com.eaglepoint.exam.compliance.repository.ComplianceReviewRepository;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.StateTransitionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.lenient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ComplianceReviewService} covering review creation,
 * approval, rejection, and status checks.
 */
@ExtendWith(MockitoExtension.class)
class ComplianceServiceTest {

    @Mock
    private ComplianceReviewRepository reviewRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private ContentSafeguardService contentSafeguardService;

    @InjectMocks
    private ComplianceReviewService complianceReviewService;

    @BeforeEach
    void setUp() {
        RequestContext.set(1L, "coordinator1", Role.ACADEMIC_COORDINATOR, "session-1", "127.0.0.1", "trace-1");
        lenient().when(contentSafeguardService.screenContent(anyString(), anyString(), anyLong()))
                .thenReturn(new ContentSafeguardService.ScreeningResult(true, List.of()));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private ComplianceReview createReview(Long id, ComplianceReviewStatus status) {
        ComplianceReview review = new ComplianceReview();
        ReflectionTestUtils.setField(review, "id", id);
        review.setEntityType("Notification");
        review.setEntityId(10L);
        review.setStatus(status);
        review.setSubmittedBy(1L);
        return review;
    }

    @Test
    void testCreatePendingReview() {
        when(reviewRepository.save(any(ComplianceReview.class))).thenAnswer(inv -> {
            ComplianceReview r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 1L);
            return r;
        });

        ComplianceReview review = complianceReviewService.createReview("Notification", 10L);

        assertNotNull(review);
        assertEquals(ComplianceReviewStatus.PENDING, review.getStatus());
        assertEquals("Notification", review.getEntityType());
        assertEquals(10L, review.getEntityId());
        verify(auditService).logAction(eq("CREATE_COMPLIANCE_REVIEW"), eq("Notification"), eq(10L), isNull(), isNull(), anyString());
    }

    @Test
    void testApproveReview() {
        ComplianceReview review = createReview(1L, ComplianceReviewStatus.PENDING);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(ComplianceReview.class))).thenAnswer(inv -> inv.getArgument(0));

        ComplianceReview result = complianceReviewService.approve(1L, "Looks good");

        assertEquals(ComplianceReviewStatus.APPROVED, result.getStatus());
        assertEquals(1L, result.getReviewedBy());
        assertEquals("Looks good", result.getComment());
        assertNotNull(result.getReviewedAt());
        verify(auditService).logAction(eq("APPROVE_COMPLIANCE_REVIEW"), anyString(), anyLong(), isNull(), isNull(), contains("Approved"));
    }

    @Test
    void testRejectReview() {
        ComplianceReview review = createReview(2L, ComplianceReviewStatus.PENDING);
        when(reviewRepository.findById(2L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(ComplianceReview.class))).thenAnswer(inv -> inv.getArgument(0));

        ComplianceReview result = complianceReviewService.reject(2L, "Content inappropriate", null);

        assertEquals(ComplianceReviewStatus.REJECTED, result.getStatus());
        assertEquals("Content inappropriate", result.getComment());
        assertNotNull(result.getReviewedAt());
    }

    @Test
    void testIsApprovedTrue() {
        ComplianceReview approved = createReview(1L, ComplianceReviewStatus.APPROVED);
        when(reviewRepository.findTopByEntityTypeAndEntityIdOrderByCreatedAtDesc("Notification", 10L))
                .thenReturn(Optional.of(approved));

        assertTrue(complianceReviewService.isApproved("Notification", 10L));
    }

    @Test
    void testIsApprovedFalse() {
        // No review exists
        when(reviewRepository.findTopByEntityTypeAndEntityIdOrderByCreatedAtDesc("Notification", 99L))
                .thenReturn(Optional.empty());

        assertFalse(complianceReviewService.isApproved("Notification", 99L));

        // Rejected review
        ComplianceReview rejected = createReview(3L, ComplianceReviewStatus.REJECTED);
        when(reviewRepository.findTopByEntityTypeAndEntityIdOrderByCreatedAtDesc("Notification", 10L))
                .thenReturn(Optional.of(rejected));

        assertFalse(complianceReviewService.isApproved("Notification", 10L));
    }

    @Test
    void testDoubleApprove() {
        ComplianceReview alreadyApproved = createReview(1L, ComplianceReviewStatus.APPROVED);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(alreadyApproved));

        assertThrows(StateTransitionException.class,
                () -> complianceReviewService.approve(1L, "Double approve attempt"));
    }
}
