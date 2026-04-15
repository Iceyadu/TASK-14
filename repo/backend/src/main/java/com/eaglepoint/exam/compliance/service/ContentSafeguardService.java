package com.eaglepoint.exam.compliance.service;

import com.eaglepoint.exam.audit.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service enforcing sensitive-content and minor-protection safeguards.
 * <p>
 * All student-visible content must pass through these checks before being
 * submitted for compliance review. This provides automated first-pass
 * screening before human compliance review.
 * <p>
 * Safeguards include:
 * <ul>
 *   <li>Sensitive content detection (profanity, violence, adult themes)</li>
 *   <li>Minor-protection screening (age-inappropriate references)</li>
 *   <li>Health-related disclaimer enforcement</li>
 *   <li>PII leak detection in free-text fields</li>
 * </ul>
 */
@Service
public class ContentSafeguardService {

    private static final Logger log = LoggerFactory.getLogger(ContentSafeguardService.class);

    /**
     * Blocked terms that indicate potentially inappropriate content for minors.
     * In production, this would be loaded from a configurable database table
     * or external content moderation API.
     */
    private static final Set<String> BLOCKED_TERMS = Set.of(
            "violence", "explicit", "abuse", "drug", "narcotic",
            "gambling", "weapon", "harassment", "threat", "hate"
    );

    /** Patterns that may indicate PII leaking into free-text content fields. */
    private static final List<Pattern> PII_PATTERNS = List.of(
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),     // SSN-like
            Pattern.compile("\\b\\d{15,19}\\b"),                 // credit card-like
            Pattern.compile("\\b\\d{3}-\\d{4}-\\d{4}\\b"),      // phone number-like
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}") // email
    );

    /** Required disclaimer text for health-related content. */
    private static final String HEALTH_DISCLAIMER_MARKER = "health";
    private static final String REQUIRED_HEALTH_NOTICE =
            "This information is provided for administrative purposes only and does not constitute medical advice.";

    private final AuditService auditService;

    public ContentSafeguardService(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Result of a content safeguard screening.
     */
    public static class ScreeningResult {
        private final boolean passed;
        private final List<String> violations;

        public ScreeningResult(boolean passed, List<String> violations) {
            this.passed = passed;
            this.violations = violations;
        }

        public boolean isPassed() {
            return passed;
        }

        public List<String> getViolations() {
            return violations;
        }
    }

    /**
     * Screens text content against all safeguard rules. Must be called before
     * any student-visible content is submitted for compliance review.
     *
     * @param content    the text content to screen
     * @param entityType the type of entity (e.g., "Notification", "ExamSession")
     * @param entityId   the entity ID for audit logging
     * @return screening result indicating pass/fail with violation details
     */
    public ScreeningResult screenContent(String content, String entityType, Long entityId) {
        if (content == null || content.isBlank()) {
            return new ScreeningResult(true, List.of());
        }

        List<String> violations = new ArrayList<>();
        String contentLower = content.toLowerCase();

        // 1. Sensitive content / minor-protection check
        for (String term : BLOCKED_TERMS) {
            if (contentLower.contains(term)) {
                violations.add("Content contains potentially inappropriate term for minors: '" + term + "'");
            }
        }

        // 2. PII leak detection
        for (Pattern pattern : PII_PATTERNS) {
            if (pattern.matcher(content).find()) {
                violations.add("Content may contain personally identifiable information (PII pattern detected)");
                break; // One PII warning is sufficient
            }
        }

        // 3. Health-related disclaimer enforcement
        if (contentLower.contains(HEALTH_DISCLAIMER_MARKER)
                && !content.contains(REQUIRED_HEALTH_NOTICE)) {
            violations.add("Health-related content must include the required administrative disclaimer");
        }

        boolean passed = violations.isEmpty();

        // Audit the screening
        auditService.logAction(
                passed ? "CONTENT_SAFEGUARD_PASSED" : "CONTENT_SAFEGUARD_FLAGGED",
                entityType, entityId, null, null,
                passed ? "Content passed all safeguard checks"
                        : "Content flagged: " + String.join("; ", violations));

        if (!passed) {
            log.warn("Content safeguard flagged {}#{}: {}", entityType, entityId, violations);
        }

        return new ScreeningResult(passed, violations);
    }

    /**
     * Screens notification content including title and body.
     */
    public ScreeningResult screenNotificationContent(String title, String body,
                                                      Long notificationId) {
        String combined = (title != null ? title : "") + " " + (body != null ? body : "");
        return screenContent(combined.trim(), "Notification", notificationId);
    }

    /**
     * Screens exam session name and related metadata for safeguard compliance.
     */
    public ScreeningResult screenExamSessionContent(String sessionName, Long sessionId) {
        return screenContent(sessionName, "ExamSession", sessionId);
    }
}
