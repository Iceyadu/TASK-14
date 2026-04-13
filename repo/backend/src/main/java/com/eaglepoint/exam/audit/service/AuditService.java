package com.eaglepoint.exam.audit.service;

import com.eaglepoint.exam.audit.model.AuditLog;
import com.eaglepoint.exam.audit.repository.AuditLogRepository;
import com.eaglepoint.exam.shared.context.RequestContext;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service responsible for recording and querying audit trail entries.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /** Field names that must be redacted from free-form audit details. */
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "password", "secret", "token", "signing_key", "signingKey",
            "student_id_number", "studentIdNumber", "guardian_contact",
            "guardianContact", "accommodation_notes", "accommodationNotes",
            "ssn", "credit_card", "creditCard"
    );

    /** Patterns that match sensitive data formats (SSN, emails, etc.). */
    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),         // SSN
            Pattern.compile("\\b\\d{15,19}\\b"),                     // credit card-like
            Pattern.compile("(?i)password\\s*[:=]\\s*\\S+"),         // password=value
            Pattern.compile("(?i)token\\s*[:=]\\s*\\S+")             // token=value
    );

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Logs an auditable action performed by the current user.
     *
     * @param action     the action name (e.g. "LOGIN", "CREATE_SESSION")
     * @param entityType the type of entity affected (e.g. "ExamSession")
     * @param entityId   the ID of the affected entity (nullable)
     * @param oldState   JSON representation of the previous state (nullable)
     * @param newState   JSON representation of the new state (nullable)
     * @param details    free-form details about the action (nullable)
     */
    @Transactional
    public void logAction(String action, String entityType, Long entityId,
                          String oldState, String newState, String details) {
        Long userId = RequestContext.getUserId();
        String ipAddress = RequestContext.getIpAddress();
        String sessionId = RequestContext.getSessionId();
        String traceId = RequestContext.getTraceId();
        String username = RequestContext.getUsername();

        String redactedDetails = redactSensitiveData(details);

        log.info("AUDIT | user={}({}) ip={} action={} entity={}#{} details={}",
                username, userId, ipAddress, action, entityType, entityId, redactedDetails);

        AuditLog entry = new AuditLog();
        entry.setUserId(userId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setOldState(redactSensitiveData(oldState));
        entry.setNewState(redactSensitiveData(newState));
        entry.setDetailsJson(redactedDetails);
        entry.setIpAddress(ipAddress);
        entry.setSessionId(sessionId);
        entry.setTraceId(traceId);

        auditLogRepository.save(entry);
    }

    /**
     * Queries the audit log with dynamic filtering criteria. All filter parameters are optional.
     *
     * @param userId     filter by the user who performed the action
     * @param entityType filter by entity type
     * @param action     filter by action name
     * @param from       start of the date range (inclusive)
     * @param to         end of the date range (inclusive)
     * @param pageable   pagination parameters
     * @return paginated audit log entries matching the filters
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> queryAuditLog(Long userId, String entityType, String action,
                                         LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable);
    }

    /**
     * Redacts sensitive data from free-form text to prevent PII leaks in audit logs.
     * Checks for known sensitive field names and data patterns.
     */
    static String redactSensitiveData(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        // Redact sensitive patterns (SSN, credit card-like, password=, token=)
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            result = pattern.matcher(result).replaceAll("[REDACTED]");
        }

        // Check if any sensitive keywords appear and warn
        String lowerText = result.toLowerCase();
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lowerText.contains(keyword.toLowerCase())) {
                // Replace the value portion after the keyword
                result = result.replaceAll(
                        "(?i)(" + Pattern.quote(keyword) + "\\s*[:=]?\\s*)(\\S+)",
                        "$1[REDACTED]");
            }
        }

        return result;
    }
}
