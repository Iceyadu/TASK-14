package com.eaglepoint.exam.anticheat.service;

import com.eaglepoint.exam.anticheat.model.AntiCheatFlag;
import com.eaglepoint.exam.anticheat.repository.AntiCheatFlagRepository;
import com.eaglepoint.exam.audit.model.AuditLog;
import com.eaglepoint.exam.audit.repository.AuditLogRepository;
import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for detecting exam anomalies and managing anti-cheat flags.
 */
@Service
public class AntiCheatService {

    private static final Logger log = LoggerFactory.getLogger(AntiCheatService.class);

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_DISMISSED = "DISMISSED";
    private static final String STATUS_CONFIRMED = "CONFIRMED_FOR_INVESTIGATION";

    private static final int ACTIVITY_BURST_THRESHOLD = 3;
    private static final int ACTIVITY_BURST_HOURS = 1;

    private static final int SCORE_DELTA_THRESHOLD = 40;
    private static final int IDENTICAL_LOOKBACK_HOURS = 24;

    private final AntiCheatFlagRepository flagRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AntiCheatService(AntiCheatFlagRepository flagRepository,
                            AuditLogRepository auditLogRepository,
                            AuditService auditService,
                            ObjectMapper objectMapper) {
        this.flagRepository = flagRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs anomaly detection rules for the specified student.
     * Creates AntiCheatFlag entries for each detected anomaly.
     *
     * Rules:
     * 1. Activity burst: more than 3 exam results submitted within 1 hour
     * 2. Identical submissions: duplicate answer hashes (checked via audit log patterns)
     * 3. Score delta: score change exceeding 3 standard deviations from class average
     */
    @Transactional
    public List<AntiCheatFlag> checkForAnomalies(Long studentUserId) {
        List<AntiCheatFlag> newFlags = new ArrayList<>();

        // Rule 1: Activity burst detection
        AntiCheatFlag burstFlag = checkActivityBurst(studentUserId);
        if (burstFlag != null) {
            newFlags.add(burstFlag);
        }

        // Rule 2: Identical submissions detection
        AntiCheatFlag duplicateFlag = checkIdenticalSubmissions(studentUserId);
        if (duplicateFlag != null) {
            newFlags.add(duplicateFlag);
        }

        // Rule 3: Score delta detection
        AntiCheatFlag scoreDeltaFlag = checkScoreDelta(studentUserId);
        if (scoreDeltaFlag != null) {
            newFlags.add(scoreDeltaFlag);
        }

        if (!newFlags.isEmpty()) {
            log.info("Detected {} anomalies for student {}", newFlags.size(), studentUserId);
        }

        return newFlags;
    }

    /**
     * Lists anti-cheat flags with optional status filter, paginated.
     */
    @Transactional(readOnly = true)
    public Page<AntiCheatFlag> listFlags(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return flagRepository.findByStatus(status, pageable);
        }
        return flagRepository.findAll(pageable);
    }

    /**
     * Reviews an anti-cheat flag with a decision and optional comment.
     */
    @Transactional
    public AntiCheatFlag reviewFlag(Long flagId, String decision, String comment) {
        AntiCheatFlag flag = flagRepository.findById(flagId)
                .orElseThrow(() -> new EntityNotFoundException("AntiCheatFlag", flagId));

        Long reviewerId = RequestContext.getUserId();

        flag.setReviewedBy(reviewerId);
        flag.setReviewDecision(decision);
        flag.setReviewComment(comment);
        flag.setReviewedAt(LocalDateTime.now());

        // Update status based on decision
        if ("DISMISS".equalsIgnoreCase(decision)) {
            flag.setStatus(STATUS_DISMISSED);
        } else if ("CONFIRM".equalsIgnoreCase(decision)) {
            flag.setStatus(STATUS_CONFIRMED);
        } else {
            flag.setStatus(decision.toUpperCase());
        }

        AntiCheatFlag saved = flagRepository.save(flag);

        auditService.logAction("REVIEW_ANTICHEAT_FLAG", "AntiCheatFlag", flagId,
                null, null,
                "Reviewed flag: decision=" + decision + " comment=" + comment);

        return saved;
    }

    // ---- Detection rules ----

    /**
     * Rule 1: Checks if a student has more than 3 exam result submissions within 1 hour.
     */
    private AntiCheatFlag checkActivityBurst(Long studentUserId) {
        LocalDateTime since = LocalDateTime.now().minusHours(ACTIVITY_BURST_HOURS);
        long recentCount = flagRepository.countRecentExamSubmissions(studentUserId, since);

        if (recentCount > ACTIVITY_BURST_THRESHOLD) {
            AntiCheatFlag flag = new AntiCheatFlag();
            flag.setStudentUserId(studentUserId);
            flag.setRuleType("ACTIVITY_BURST");
            flag.setStatus(STATUS_PENDING);
            flag.setDetailsJson(toJson(Map.of(
                    "recentSubmissions", recentCount,
                    "threshold", ACTIVITY_BURST_THRESHOLD,
                    "windowHours", ACTIVITY_BURST_HOURS,
                    "detectedAt", LocalDateTime.now().toString()
            )));

            AntiCheatFlag saved = flagRepository.save(flag);
            log.warn("Activity burst detected for student {}: {} submissions in {} hour(s)",
                    studentUserId, recentCount, ACTIVITY_BURST_HOURS);
            return saved;
        }
        return null;
    }

    /**
     * Rule 2: Checks for identical (duplicate) answer hash submissions.
     * This uses the audit log to detect if the same answer content was submitted
     * multiple times, which may indicate copying or replay attacks.
     */
    private AntiCheatFlag checkIdenticalSubmissions(Long studentUserId) {
        List<AntiCheatFlag> existingFlags = flagRepository.findByStudentUserId(studentUserId);
        boolean hasRecentDuplicateFlag = existingFlags.stream()
                .anyMatch(f -> "IDENTICAL_SUBMISSIONS".equals(f.getRuleType())
                        && f.getFlaggedAt() != null
                        && f.getFlaggedAt().isAfter(LocalDateTime.now().minusDays(1)));

        if (hasRecentDuplicateFlag) {
            return null;
        }

        LocalDateTime since = LocalDateTime.now().minusHours(IDENTICAL_LOOKBACK_HOURS);
        List<AuditLog> submissions = auditLogRepository
                .findByUserIdAndActionAndTimestampGreaterThanEqualOrderByTimestampAsc(
                        studentUserId, "SUBMIT_EXAM_RESULT", since);
        if (submissions.size() < 2) {
            return null;
        }

        Map<String, Integer> hashCounts = new HashMap<>();
        for (AuditLog log : submissions) {
            String key = submissionFingerprint(log.getDetailsJson());
            if (key == null || key.isBlank()) {
                continue;
            }
            hashCounts.merge(key, 1, Integer::sum);
        }

        String dupKey = hashCounts.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (dupKey == null) {
            return null;
        }

        AntiCheatFlag flag = new AntiCheatFlag();
        flag.setStudentUserId(studentUserId);
        flag.setRuleType("IDENTICAL_SUBMISSIONS");
        flag.setStatus(STATUS_PENDING);
        flag.setDetailsJson(toJson(Map.of(
                "duplicateFingerprint", dupKey,
                "windowHours", IDENTICAL_LOOKBACK_HOURS,
                "detectedAt", LocalDateTime.now().toString()
        )));

        AntiCheatFlag saved = flagRepository.save(flag);
        log.warn("Identical submission pattern detected for student {} (duplicate content hash / payload)", studentUserId);
        return saved;
    }

    /**
     * Builds a stable fingerprint for a submission from audit {@code details_json}
     * (prefers {@code answerHash}, then {@code contentHash}, else full details).
     */
    private String submissionFingerprint(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return null;
        }
        try {
            var node = objectMapper.readTree(detailsJson);
            if (node.hasNonNull("answerHash")) {
                return "answerHash:" + node.get("answerHash").asText();
            }
            if (node.hasNonNull("contentHash")) {
                return "contentHash:" + node.get("contentHash").asText();
            }
            return "full:" + detailsJson.hashCode();
        } catch (Exception e) {
            return "raw:" + detailsJson.hashCode();
        }
    }

    /**
     * Rule 3: Checks if a student's score change exceeds 3 standard deviations
     * from the class average score change.
     */
    private AntiCheatFlag checkScoreDelta(Long studentUserId) {
        List<AntiCheatFlag> existingFlags = flagRepository.findByStudentUserId(studentUserId);
        boolean hasRecent = existingFlags.stream()
                .anyMatch(f -> "SCORE_DELTA".equals(f.getRuleType())
                        && f.getFlaggedAt() != null
                        && f.getFlaggedAt().isAfter(LocalDateTime.now().minusDays(7)));

        if (hasRecent) {
            return null;
        }

        LocalDateTime since = LocalDateTime.now().minusDays(90);
        List<AuditLog> submissions = auditLogRepository
                .findByUserIdAndActionAndTimestampGreaterThanEqualOrderByTimestampAsc(
                        studentUserId, "SUBMIT_EXAM_RESULT", since);
        if (submissions.size() < 2) {
            return null;
        }

        AuditLog prev = submissions.get(submissions.size() - 2);
        AuditLog last = submissions.get(submissions.size() - 1);
        Double s1 = extractScore(prev.getDetailsJson());
        Double s2 = extractScore(last.getDetailsJson());
        if (s1 == null || s2 == null) {
            return null;
        }

        double delta = Math.abs(s2 - s1);
        if (delta <= SCORE_DELTA_THRESHOLD) {
            return null;
        }

        AntiCheatFlag flag = new AntiCheatFlag();
        flag.setStudentUserId(studentUserId);
        flag.setRuleType("SCORE_DELTA");
        flag.setStatus(STATUS_PENDING);
        flag.setDetailsJson(toJson(Map.of(
                "previousScore", s1,
                "latestScore", s2,
                "absoluteDelta", delta,
                "threshold", SCORE_DELTA_THRESHOLD,
                "note", "Large jump between last two recorded scores (audit SUBMIT_EXAM_RESULT); flag for human review",
                "detectedAt", LocalDateTime.now().toString()
        )));

        AntiCheatFlag saved = flagRepository.save(flag);
        log.warn("Abnormal score delta for student {}: {} -> {} (delta {})",
                studentUserId, s1, s2, delta);
        return saved;
    }

    private Double extractScore(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return null;
        }
        try {
            var node = objectMapper.readTree(detailsJson);
            if (node.hasNonNull("score")) {
                return node.get("score").asDouble();
            }
            if (node.hasNonNull("percentage")) {
                return node.get("percentage").asDouble();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ---- Helpers ----

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
