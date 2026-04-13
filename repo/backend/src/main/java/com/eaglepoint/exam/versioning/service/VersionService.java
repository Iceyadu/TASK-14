package com.eaglepoint.exam.versioning.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.compliance.service.ComplianceReviewService;
import com.eaglepoint.exam.notifications.model.Notification;
import com.eaglepoint.exam.notifications.repository.NotificationRepository;
import com.eaglepoint.exam.roster.model.RosterEntry;
import com.eaglepoint.exam.roster.repository.RosterEntryRepository;
import com.eaglepoint.exam.scheduling.dto.ExamSessionResponse;
import com.eaglepoint.exam.scheduling.model.ExamSession;
import com.eaglepoint.exam.scheduling.model.ExamSessionClass;
import com.eaglepoint.exam.scheduling.repository.ExamSessionClassRepository;
import com.eaglepoint.exam.scheduling.repository.ExamSessionRepository;
import com.eaglepoint.exam.security.service.IdempotencyService;
import com.eaglepoint.exam.security.service.ScopeService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.AccessDeniedException;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import com.eaglepoint.exam.versioning.model.EntityVersion;
import com.eaglepoint.exam.versioning.repository.EntityVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for creating, retrieving, comparing, and restoring entity versions.
 */
@Service
public class VersionService {

    private static final Logger log = LoggerFactory.getLogger(VersionService.class);

    /**
     * Entity types that are considered published/student-visible and require
     * compliance re-review when restored.
     */
    private static final Set<String> STUDENT_VISIBLE_ENTITIES = Set.of(
            "Notification", "ExamSession"
    );

    private final EntityVersionRepository versionRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final ComplianceReviewService complianceReviewService;
    private final ExamSessionRepository examSessionRepository;
    private final ExamSessionClassRepository examSessionClassRepository;
    private final RosterEntryRepository rosterEntryRepository;
    private final NotificationRepository notificationRepository;
    private final ScopeService scopeService;

    public VersionService(EntityVersionRepository versionRepository,
                          ObjectMapper objectMapper,
                          AuditService auditService,
                          IdempotencyService idempotencyService,
                          ComplianceReviewService complianceReviewService,
                          ExamSessionRepository examSessionRepository,
                          ExamSessionClassRepository examSessionClassRepository,
                          RosterEntryRepository rosterEntryRepository,
                          NotificationRepository notificationRepository,
                          ScopeService scopeService) {
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.idempotencyService = idempotencyService;
        this.complianceReviewService = complianceReviewService;
        this.examSessionRepository = examSessionRepository;
        this.examSessionClassRepository = examSessionClassRepository;
        this.rosterEntryRepository = rosterEntryRepository;
        this.notificationRepository = notificationRepository;
        this.scopeService = scopeService;
    }

    /**
     * Creates a new version snapshot for the given entity.
     * Automatically determines the next version number.
     */
    @Transactional
    public EntityVersion createVersion(String entityType, Long entityId, Object snapshot) {
        Long userId = RequestContext.getUserId();

        long existingCount = versionRepository.countByEntityTypeAndEntityId(entityType, entityId);
        int nextVersion = (int) existingCount + 1;

        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize version snapshot", e);
        }

        EntityVersion version = new EntityVersion();
        version.setEntityType(entityType);
        version.setEntityId(entityId);
        version.setVersionNumber(nextVersion);
        version.setSnapshotJson(snapshotJson);
        version.setCreatedBy(userId);

        EntityVersion saved = versionRepository.save(version);

        auditService.logAction("CREATE_VERSION", entityType, entityId,
                null, null,
                "Created version " + nextVersion + " for " + entityType + "#" + entityId);

        return saved;
    }

    /**
     * Returns all versions for an entity, ordered by version number descending.
     */
    @Transactional(readOnly = true)
    public List<EntityVersion> getVersions(String entityType, Long entityId) {
        enforceVersionAccess(entityType, entityId);
        return versionRepository.findByEntityTypeAndEntityIdOrderByVersionNumberDesc(entityType, entityId);
    }

    /**
     * Returns a specific version of an entity.
     */
    @Transactional(readOnly = true)
    public EntityVersion getVersion(String entityType, Long entityId, int versionNumber) {
        enforceVersionAccess(entityType, entityId);
        return versionRepository.findByEntityTypeAndEntityIdAndVersionNumber(
                        entityType, entityId, versionNumber)
                .orElseThrow(() -> new EntityNotFoundException(
                        "EntityVersion",
                        entityType + "#" + entityId + " v" + versionNumber));
    }

    /**
     * Returns both version snapshots for client-side comparison.
     */
    @Transactional(readOnly = true)
    public Map<String, EntityVersion> compareVersions(String entityType, Long entityId,
                                                       int fromVersion, int toVersion) {
        enforceVersionAccess(entityType, entityId);
        EntityVersion from = versionRepository.findByEntityTypeAndEntityIdAndVersionNumber(
                        entityType, entityId, fromVersion)
                .orElseThrow(() -> new EntityNotFoundException(
                        "EntityVersion",
                        entityType + "#" + entityId + " v" + fromVersion));
        EntityVersion to = versionRepository.findByEntityTypeAndEntityIdAndVersionNumber(
                        entityType, entityId, toVersion)
                .orElseThrow(() -> new EntityNotFoundException(
                        "EntityVersion",
                        entityType + "#" + entityId + " v" + toVersion));
        return Map.of("from", from, "to", to);
    }

    /**
     * Restores an entity to a previous version by creating a new version with
     * the target version's snapshot data. If the entity is student-visible,
     * triggers a compliance re-review.
     */
    @Transactional
    public EntityVersion restoreVersion(String entityType, Long entityId,
                                         int targetVersion, String idempotencyKey) {
        Long userId = RequestContext.getUserId();

        // Check idempotency
        if (idempotencyKey != null) {
            Object existing = idempotencyService.checkAndStore(
                    idempotencyKey, userId, "RESTORE_VERSION");
            if (existing != null) {
                log.info("Idempotent duplicate detected for RESTORE_VERSION key={}", idempotencyKey);
                return null; // duplicate request
            }
        }

        enforceVersionAccess(entityType, entityId);

        // Load target version
        EntityVersion targetVer = versionRepository.findByEntityTypeAndEntityIdAndVersionNumber(
                        entityType, entityId, targetVersion)
                .orElseThrow(() -> new EntityNotFoundException(
                        "EntityVersion",
                        entityType + "#" + entityId + " v" + targetVersion));

        try {
            applySnapshotToLiveEntity(entityType, entityId, targetVer.getSnapshotJson());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to restore entity from snapshot", e);
        }

        // Create a new version with the restored snapshot
        long existingCount = versionRepository.countByEntityTypeAndEntityId(entityType, entityId);
        int nextVersion = (int) existingCount + 1;

        EntityVersion restored = new EntityVersion();
        restored.setEntityType(entityType);
        restored.setEntityId(entityId);
        restored.setVersionNumber(nextVersion);
        restored.setSnapshotJson(targetVer.getSnapshotJson());
        restored.setCreatedBy(userId);

        EntityVersion saved = versionRepository.save(restored);

        // Trigger compliance re-review for student-visible content
        if (STUDENT_VISIBLE_ENTITIES.contains(entityType)) {
            complianceReviewService.createReview(entityType, entityId);
            log.info("Triggered compliance re-review for restored {}#{}", entityType, entityId);
        }

        auditService.logAction("RESTORE_VERSION", entityType, entityId,
                null, null,
                "Restored " + entityType + "#" + entityId + " to version " + targetVersion
                        + " (new version " + nextVersion + ")");

        if (idempotencyKey != null) {
            idempotencyService.storeResponse(idempotencyKey, userId, "RESTORE_VERSION", saved);
        }

        return saved;
    }

    private void applySnapshotToLiveEntity(String entityType, Long entityId, String snapshotJson)
            throws JsonProcessingException {
        switch (entityType) {
            case "ExamSession" -> {
                ExamSessionResponse snap = objectMapper.readValue(snapshotJson, ExamSessionResponse.class);
                ExamSession session = examSessionRepository.findById(entityId)
                        .orElseThrow(() -> new EntityNotFoundException("ExamSession", entityId));
                session.setName(snap.getName());
                session.setTermId(snap.getTermId());
                session.setCourseId(snap.getCourseId());
                session.setCampusId(snap.getCampusId());
                session.setRoomId(snap.getRoomId());
                session.setScheduledDate(snap.getExamDate());
                session.setStartTime(snap.getStartTime());
                session.setEndTime(snap.getEndTime());
                session.setStatus(snap.getStatus());
                examSessionRepository.save(session);
                examSessionClassRepository.deleteByExamSessionId(entityId);
                if (snap.getClassIds() != null) {
                    for (Long classId : snap.getClassIds()) {
                        examSessionClassRepository.save(new ExamSessionClass(entityId, classId));
                    }
                }
            }
            case "RosterEntry" -> {
                RosterEntry snap = objectMapper.readValue(snapshotJson, RosterEntry.class);
                RosterEntry entry = rosterEntryRepository.findById(entityId)
                        .orElseThrow(() -> new EntityNotFoundException("RosterEntry", entityId));
                entry.setStudentUserId(snap.getStudentUserId());
                entry.setClassId(snap.getClassId());
                entry.setTermId(snap.getTermId());
                entry.setStudentIdNumberEnc(snap.getStudentIdNumberEnc());
                entry.setGuardianContactEnc(snap.getGuardianContactEnc());
                entry.setAccommodationNotesEnc(snap.getAccommodationNotesEnc());
                rosterEntryRepository.save(entry);
            }
            case "Notification" -> {
                Notification snap = objectMapper.readValue(snapshotJson, Notification.class);
                Notification n = notificationRepository.findById(entityId)
                        .orElseThrow(() -> new EntityNotFoundException("Notification", entityId));
                n.setTitle(snap.getTitle());
                n.setContent(snap.getContent());
                n.setEventType(snap.getEventType());
                n.setTargetType(snap.getTargetType());
                n.setStatus(snap.getStatus());
                notificationRepository.save(n);
            }
            default -> log.warn("No live entity restore implemented for {}", entityType);
        }
    }

    private void enforceVersionAccess(String entityType, Long entityId) {
        Long userId = RequestContext.getUserId();
        Role role = RequestContext.getRole();
        switch (entityType) {
            case "ExamSession" -> {
                ExamSession session = examSessionRepository.findById(entityId)
                        .orElseThrow(() -> new EntityNotFoundException("ExamSession", entityId));
                List<Long> classIds = examSessionClassRepository.findByExamSessionId(entityId).stream()
                        .map(ExamSessionClass::getClassId)
                        .toList();
                scopeService.enforceExamSessionScope(
                        userId, role,
                        session.getCampusId(), session.getTermId(), session.getCourseId(), classIds);
            }
            case "RosterEntry" -> {
                RosterEntry entry = rosterEntryRepository.findById(entityId)
                        .orElseThrow(() -> new EntityNotFoundException("RosterEntry", entityId));
                scopeService.enforceScope(userId, role, "ROSTER_ENTRY", entry.getClassId());
            }
            case "Notification" -> {
                Notification n = notificationRepository.findById(entityId)
                        .orElseThrow(() -> new EntityNotFoundException("Notification", entityId));
                scopeService.enforceNotificationEntityAccess(n);
            }
            default -> throw new AccessDeniedException("Version access is not defined for entity type: " + entityType);
        }
    }
}
