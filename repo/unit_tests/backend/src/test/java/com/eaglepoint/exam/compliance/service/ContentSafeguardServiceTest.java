package com.eaglepoint.exam.compliance.service;

import com.eaglepoint.exam.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContentSafeguardServiceTest {

    @Mock private AuditService auditService;

    // ---- blocked terms + PII ----

    @Test
    void testFlagsBlockedTermAndPiiPattern() {
        ContentSafeguardService service = new ContentSafeguardService(auditService);
        var result = service.screenContent(
                "This contains violence and email test@example.com",
                "Notification",
                10L);

        assertFalse(result.isPassed());
        assertTrue(result.getViolations().stream().anyMatch(v -> v.contains("violence")));
        assertTrue(result.getViolations().stream().anyMatch(v -> v.contains("PII")));
        verify(auditService).logAction(eq("CONTENT_SAFEGUARD_FLAGGED"),
                eq("Notification"), eq(10L), eq(null), eq(null), contains("Content flagged"));
    }

    // ---- clean content passes ----

    @Test
    void testPassesCleanContent() {
        ContentSafeguardService service = new ContentSafeguardService(auditService);
        var result = service.screenExamSessionContent("Math Midterm Session", 22L);

        assertTrue(result.isPassed());
        verify(auditService).logAction(eq("CONTENT_SAFEGUARD_PASSED"),
                eq("ExamSession"), eq(22L), eq(null), eq(null), contains("passed"));
    }

    // ---- health content without disclaimer ----

    @Test
    void testHealthContentRequiresDisclaimer() {
        ContentSafeguardService service = new ContentSafeguardService(auditService);
        var result = service.screenContent(
                "health screening details for students",
                "Notification",
                33L);

        assertFalse(result.isPassed());
        assertTrue(result.getViolations().stream().anyMatch(v -> v.contains("disclaimer")));
    }

    // ---- phone number PII ----

    @Test
    void testPhoneNumberPatternTriggersViolation() {
        ContentSafeguardService service = new ContentSafeguardService(auditService);
        var result = service.screenContent(
                "Contact us at 138-0013-8001 for exam questions",
                "Notification",
                44L);

        assertFalse(result.isPassed());
        assertTrue(result.getViolations().stream().anyMatch(v -> v.contains("PII")));
    }

    // ---- multiple violations in a single screening ----

    @Test
    void testMultipleViolationsAllReported() {
        ContentSafeguardService service = new ContentSafeguardService(auditService);
        var result = service.screenContent(
                "violence and hate and email user@example.com",
                "Notification",
                55L);

        assertFalse(result.isPassed());
        assertTrue(result.getViolations().size() >= 2,
                "Expected at least 2 violations but got: " + result.getViolations());
    }

    // ---- only blocked term, no PII ----

    @Test
    void testOnlyBlockedTermNoEmailFlagsBlockedTerm() {
        ContentSafeguardService service = new ContentSafeguardService(auditService);
        var result = service.screenContent(
                "This message contains hate speech",
                "Notification",
                66L);

        assertFalse(result.isPassed());
        assertTrue(result.getViolations().stream().anyMatch(v -> v.contains("hate")));
    }
}
