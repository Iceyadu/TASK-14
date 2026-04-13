package com.eaglepoint.exam.notifications.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.compliance.service.ComplianceReviewService;
import com.eaglepoint.exam.jobs.service.JobService;
import com.eaglepoint.exam.notifications.dto.CreateNotificationRequest;
import com.eaglepoint.exam.notifications.dto.NotificationResponse;
import com.eaglepoint.exam.notifications.dto.SubscriptionPreferencesResponse;
import com.eaglepoint.exam.notifications.dto.UpdateSubscriptionRequest;
import com.eaglepoint.exam.notifications.model.DndSetting;
import com.eaglepoint.exam.notifications.model.InboxMessage;
import com.eaglepoint.exam.notifications.model.DeliveryStatusEntry;
import com.eaglepoint.exam.notifications.model.Notification;
import com.eaglepoint.exam.notifications.model.NotificationStatus;
import com.eaglepoint.exam.notifications.model.NotificationTarget;
import com.eaglepoint.exam.notifications.model.NotificationTargetType;
import com.eaglepoint.exam.notifications.model.SubscriptionSetting;
import com.eaglepoint.exam.notifications.repository.DeliveryStatusRepository;
import com.eaglepoint.exam.notifications.repository.DndSettingRepository;
import com.eaglepoint.exam.notifications.repository.InboxMessageRepository;
import com.eaglepoint.exam.notifications.repository.NotificationRepository;
import com.eaglepoint.exam.notifications.repository.NotificationTargetRepository;
import com.eaglepoint.exam.notifications.repository.SubscriptionSettingRepository;
import com.eaglepoint.exam.security.service.IdempotencyService;
import com.eaglepoint.exam.security.service.ScopeService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.enums.ScopeType;
import com.eaglepoint.exam.shared.exception.AccessDeniedException;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import com.eaglepoint.exam.shared.exception.StateTransitionException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service managing the full notification lifecycle: creation, compliance review,
 * publishing, inbox management, delivery status, and subscription settings.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationTargetRepository targetRepository;
    private final DeliveryStatusRepository deliveryStatusRepository;
    private final InboxMessageRepository inboxMessageRepository;
    private final SubscriptionSettingRepository subscriptionSettingRepository;
    private final DndSettingRepository dndSettingRepository;
    private final ScopeService scopeService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final ComplianceReviewService complianceReviewService;
    private final JobService jobService;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationTargetRepository targetRepository,
                               DeliveryStatusRepository deliveryStatusRepository,
                               InboxMessageRepository inboxMessageRepository,
                               SubscriptionSettingRepository subscriptionSettingRepository,
                               DndSettingRepository dndSettingRepository,
                               ScopeService scopeService,
                               IdempotencyService idempotencyService,
                               AuditService auditService,
                               ComplianceReviewService complianceReviewService,
                               JobService jobService) {
        this.notificationRepository = notificationRepository;
        this.targetRepository = targetRepository;
        this.deliveryStatusRepository = deliveryStatusRepository;
        this.inboxMessageRepository = inboxMessageRepository;
        this.subscriptionSettingRepository = subscriptionSettingRepository;
        this.dndSettingRepository = dndSettingRepository;
        this.scopeService = scopeService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
        this.complianceReviewService = complianceReviewService;
        this.jobService = jobService;
    }

    /**
     * Lists notifications with optional status filter. Admins see all; other staff see rows they
     * created or that target a grade/class in their scope (via {@code notification_targets}).
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> listNotifications(Pageable pageable, NotificationStatus statusFilter) {
        Long userId = RequestContext.getUserId();
        Role role = RequestContext.getRole();

        Specification<Notification> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }

            if (role != Role.ADMIN) {
                List<Long> gradeIds = scopeService.listScopeIds(userId, role, ScopeType.GRADE);
                List<Long> classIds = scopeService.listScopeIds(userId, role, ScopeType.CLASS);
                predicates.add(visibleToStaff(root, query, cb, userId, gradeIds, classIds));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return notificationRepository.findAll(spec, pageable).map(this::toResponse);
    }

    /**
     * Visibility: creator, or GRADE notification with a target grade ID in scope, or CLASS with a
     * target class ID in scope. ALL_STUDENTS / INDIVIDUAL without creator access rely on creator-only.
     */
    private Predicate visibleToStaff(Root<Notification> root,
                                     CriteriaQuery<?> query,
                                     CriteriaBuilder cb,
                                     Long userId,
                                     List<Long> gradeScopeIds,
                                     List<Long> classScopeIds) {
        Predicate owned = cb.equal(root.get("createdBy"), userId);

        Predicate gradeMatch = cb.disjunction();
        if (gradeScopeIds != null && !gradeScopeIds.isEmpty()) {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<NotificationTarget> nt = sq.from(NotificationTarget.class);
            sq.select(cb.literal(1L));
            sq.where(cb.and(
                    cb.equal(nt.get("notificationId"), root.get("id")),
                    nt.get("targetId").in(gradeScopeIds)));
            gradeMatch = cb.and(
                    cb.equal(root.get("targetType"), NotificationTargetType.GRADE),
                    cb.exists(sq));
        }

        Predicate classMatch = cb.disjunction();
        if (classScopeIds != null && !classScopeIds.isEmpty()) {
            Subquery<Long> sq2 = query.subquery(Long.class);
            Root<NotificationTarget> nt2 = sq2.from(NotificationTarget.class);
            sq2.select(cb.literal(1L));
            sq2.where(cb.and(
                    cb.equal(nt2.get("notificationId"), root.get("id")),
                    nt2.get("targetId").in(classScopeIds)));
            classMatch = cb.and(
                    cb.equal(root.get("targetType"), NotificationTargetType.CLASS),
                    cb.exists(sq2));
        }

        return cb.or(owned, gradeMatch, classMatch);
    }

    /**
     * Creates a new notification in DRAFT status. Supports idempotency.
     */
    @Transactional
    public NotificationResponse createNotification(CreateNotificationRequest request) {
        Long userId = RequestContext.getUserId();

        // Check idempotency
        if (request.getIdempotencyKey() != null) {
            Object existing = idempotencyService.checkAndStore(
                    request.getIdempotencyKey(), userId, "CREATE_NOTIFICATION");
            if (existing != null) {
                log.info("Idempotent duplicate detected for CREATE_NOTIFICATION key={}", request.getIdempotencyKey());
                // Return the stored response cast appropriately
                return mapFromIdempotentResponse(existing);
            }
        }

        validateCreateTargets(request);

        Notification notification = new Notification();
        notification.setTitle(request.getTitle());
        notification.setContent(request.getContent());
        notification.setEventType(request.getEventType());
        notification.setTargetType(request.getTargetType());
        notification.setStatus(NotificationStatus.DRAFT);
        notification.setCreatedBy(userId);

        Notification saved = notificationRepository.save(notification);

        // Save targets
        if (request.getTargetIds() != null) {
            for (Long targetId : request.getTargetIds()) {
                NotificationTarget target = new NotificationTarget(saved.getId(), targetId);
                targetRepository.save(target);
            }
        }

        auditService.logAction("CREATE_NOTIFICATION", "Notification", saved.getId(),
                null, null, "Created notification: " + saved.getTitle());

        NotificationResponse response = toResponse(saved);
        response.setTargetIds(request.getTargetIds());

        // Store idempotency result
        if (request.getIdempotencyKey() != null) {
            idempotencyService.storeResponse(
                    request.getIdempotencyKey(), userId, "CREATE_NOTIFICATION", response);
        }

        return response;
    }

    /**
     * Submits a DRAFT notification for compliance review.
     */
    @Transactional
    public NotificationResponse submitForReview(Long id) {
        Notification notification = findOrThrow(id);
        enforceNotificationAccess(notification);

        if (notification.getStatus() != NotificationStatus.DRAFT) {
            throw new StateTransitionException(
                    notification.getStatus().name(), "REVIEW",
                    "Only DRAFT notifications can be submitted for review");
        }

        complianceReviewService.createReview("Notification", notification.getId());

        auditService.logAction("SUBMIT_NOTIFICATION_REVIEW", "Notification", id,
                null, null, "Submitted notification for compliance review");

        return toResponse(notification);
    }

    /**
     * Publishes a notification: checks compliance approval, transitions to QUEUED,
     * and creates delivery jobs.
     */
    @Transactional
    public NotificationResponse publishNotification(Long id, String idempotencyKey) {
        Long userId = RequestContext.getUserId();

        // Check idempotency
        if (idempotencyKey != null) {
            Object existing = idempotencyService.checkAndStore(idempotencyKey, userId, "PUBLISH_NOTIFICATION");
            if (existing != null) {
                log.info("Idempotent duplicate detected for PUBLISH_NOTIFICATION key={}", idempotencyKey);
                return mapFromIdempotentResponse(existing);
            }
        }

        Notification notification = findOrThrow(id);
        enforceNotificationAccess(notification);

        // Check compliance approval
        boolean approved = complianceReviewService.isApproved("Notification", id);
        if (!approved) {
            throw new StateTransitionException(
                    notification.getStatus().name(), "QUEUED",
                    "Notification must be compliance-approved before publishing");
        }

        // Validate transition
        if (!notification.getStatus().canTransitionTo(NotificationStatus.QUEUED)) {
            throw new StateTransitionException(
                    notification.getStatus().name(), NotificationStatus.QUEUED.name());
        }

        notification.setStatus(NotificationStatus.QUEUED);
        Notification saved = notificationRepository.save(notification);

        // Create a delivery job
        String dedupKey = "NOTIFICATION_SEND_" + id;
        jobService.enqueueJob("NOTIFICATION_SEND", id, dedupKey, userId);

        auditService.logAction("PUBLISH_NOTIFICATION", "Notification", id,
                null, null, "Published notification, queued for delivery");

        NotificationResponse response = toResponse(saved);

        if (idempotencyKey != null) {
            idempotencyService.storeResponse(idempotencyKey, userId, "PUBLISH_NOTIFICATION", response);
        }

        return response;
    }

    /**
     * Cancels a DRAFT or QUEUED notification.
     */
    @Transactional
    public NotificationResponse cancelNotification(Long id) {
        Notification notification = findOrThrow(id);
        enforceNotificationAccess(notification);

        if (notification.getStatus() != NotificationStatus.DRAFT
                && notification.getStatus() != NotificationStatus.QUEUED) {
            throw new StateTransitionException(
                    notification.getStatus().name(), NotificationStatus.CANCELED.name(),
                    "Only DRAFT or QUEUED notifications can be canceled");
        }

        notification.setStatus(NotificationStatus.CANCELED);
        Notification saved = notificationRepository.save(notification);

        auditService.logAction("CANCEL_NOTIFICATION", "Notification", id,
                null, null, "Canceled notification");

        return toResponse(saved);
    }

    /**
     * Returns inbox messages for a student, optionally filtered by read status.
     */
    @Transactional(readOnly = true)
    public Page<InboxMessage> getStudentInbox(Long studentUserId, Pageable pageable, Boolean readFilter) {
        if (readFilter != null) {
            return inboxMessageRepository.findByStudentUserIdAndIsReadOrderByCreatedAtDesc(
                    studentUserId, readFilter, pageable);
        }
        return inboxMessageRepository.findByStudentUserIdOrderByCreatedAtDesc(studentUserId, pageable);
    }

    /**
     * Marks an inbox message as read. The message must belong to the requesting student.
     */
    @Transactional
    public void markRead(Long inboxId, Long studentUserId) {
        InboxMessage message = inboxMessageRepository.findById(inboxId)
                .orElseThrow(() -> new EntityNotFoundException("InboxMessage", inboxId));

        if (!message.getStudentUserId().equals(studentUserId)) {
            throw new AccessDeniedException("Cannot mark another student's message as read");
        }

        message.setIsRead(true);
        inboxMessageRepository.save(message);
    }

    /**
     * Returns per-student delivery status entries for a notification.
     */
    @Transactional(readOnly = true)
    public List<DeliveryStatusEntry> getDeliveryStatus(Long notificationId) {
        Notification notification = findOrThrow(notificationId);
        enforceNotificationAccess(notification);
        return deliveryStatusRepository.findByNotificationId(notificationId);
    }

    /**
     * Returns the subscription settings for a student.
     */
    @Transactional(readOnly = true)
    public List<SubscriptionSetting> getSubscriptionSettings(Long studentUserId) {
        return subscriptionSettingRepository.findByStudentUserId(studentUserId);
    }

    /**
     * Subscription toggles and DND window for API responses (student preferences UI).
     */
    @Transactional(readOnly = true)
    public SubscriptionPreferencesResponse getSubscriptionPreferences(Long studentUserId) {
        List<SubscriptionSetting> list = subscriptionSettingRepository.findByStudentUserId(studentUserId);
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (SubscriptionSetting s : list) {
            map.put(s.getEventType(), s.isEnabled());
        }
        LocalTime dndStart = null;
        LocalTime dndEnd = null;
        Optional<DndSetting> dnd = dndSettingRepository.findByStudentUserId(studentUserId);
        if (dnd.isPresent()) {
            dndStart = dnd.get().getDndStart();
            dndEnd = dnd.get().getDndEnd();
        }
        return new SubscriptionPreferencesResponse(map, dndStart, dndEnd);
    }

    /**
     * Updates a student's subscription settings and DND configuration.
     */
    @Transactional
    public void updateSubscriptionSettings(Long studentUserId, UpdateSubscriptionRequest request) {
        // Update subscription preferences
        if (request.getSettings() != null) {
            for (UpdateSubscriptionRequest.SubscriptionEntry entry : request.getSettings()) {
                SubscriptionSetting setting = subscriptionSettingRepository
                        .findByStudentUserIdAndEventType(studentUserId, entry.getEventType())
                        .orElse(new SubscriptionSetting(studentUserId, entry.getEventType(), entry.isEnabled()));

                setting.setEnabled(entry.isEnabled());
                subscriptionSettingRepository.save(setting);
            }
        }

        // Update DND settings
        if (request.getDndStart() != null || request.getDndEnd() != null) {
            DndSetting dndSetting = dndSettingRepository.findByStudentUserId(studentUserId)
                    .orElse(new DndSetting(studentUserId, null, null));

            if (request.getDndStart() != null) {
                dndSetting.setDndStart(request.getDndStart());
            }
            if (request.getDndEnd() != null) {
                dndSetting.setDndEnd(request.getDndEnd());
            }
            dndSettingRepository.save(dndSetting);
        }
    }

    // ---- Private helpers ----

    /**
     * Ensures create-time targets are within the caller's scope (non-admin).
     */
    private void validateCreateTargets(CreateNotificationRequest request) {
        Long uid = RequestContext.getUserId();
        Role role = RequestContext.getRole();
        if (role == Role.ADMIN) {
            return;
        }
        NotificationTargetType targetType = request.getTargetType();
        List<Long> targetIds = request.getTargetIds();
        if (targetType == null) {
            return;
        }
        switch (targetType) {
            case ALL_STUDENTS -> {
                throw new AccessDeniedException("Only administrators can create notifications for all students");
            }
            case GRADE -> {
                if (targetIds == null || targetIds.isEmpty()) {
                    return;
                }
                for (Long gradeId : targetIds) {
                    scopeService.enforceScope(uid, role, "NOTIFICATION", gradeId);
                }
            }
            case CLASS -> {
                if (targetIds == null || targetIds.isEmpty()) {
                    return;
                }
                for (Long classId : targetIds) {
                    scopeService.enforceScope(uid, role, "CLASS", classId);
                }
            }
            case INDIVIDUAL -> {
                if (targetIds == null || targetIds.isEmpty()) {
                    return;
                }
                throw new AccessDeniedException(
                        "Only administrators can target individual students; use class or grade scope instead");
            }
            default -> {
            }
        }
    }

    /**
     * Object-level authorization for staff workflows: creator, administrator,
     * or scoped access aligned with notification target type.
     */
    private void enforceNotificationAccess(Notification notification) {
        Long uid = RequestContext.getUserId();
        Role role = RequestContext.getRole();
        if (role == Role.ADMIN) {
            return;
        }
        if (notification.getCreatedBy() != null && notification.getCreatedBy().equals(uid)) {
            return;
        }

        List<NotificationTarget> targets = targetRepository.findByNotificationId(notification.getId());
        NotificationTargetType targetType = notification.getTargetType();
        if (targets.isEmpty() || targetType == null) {
            throw new AccessDeniedException("You do not have access to this notification");
        }

        switch (targetType) {
            case ALL_STUDENTS -> throw new AccessDeniedException(
                    "Only the creator or an administrator can manage this notification");
            case GRADE -> {
                for (NotificationTarget t : targets) {
                    scopeService.enforceScope(uid, role, "NOTIFICATION", t.getTargetId());
                }
            }
            case CLASS -> {
                for (NotificationTarget t : targets) {
                    scopeService.enforceScope(uid, role, "CLASS", t.getTargetId());
                }
            }
            case INDIVIDUAL -> throw new AccessDeniedException(
                    "Only the creator or an administrator can manage individually targeted notifications");
            default -> throw new AccessDeniedException("Unsupported notification target type");
        }
    }

    private Notification findOrThrow(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification", id));
    }

    private NotificationResponse toResponse(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setTitle(notification.getTitle());
        response.setContent(notification.getContent());
        response.setEventType(notification.getEventType());
        response.setTargetType(notification.getTargetType());
        response.setStatus(notification.getStatus());
        response.setCreatedBy(notification.getCreatedBy());
        response.setCreatedAt(notification.getCreatedAt());
        response.setUpdatedAt(notification.getUpdatedAt());

        List<Long> targetIds = targetRepository.findByNotificationId(notification.getId())
                .stream()
                .map(NotificationTarget::getTargetId)
                .collect(Collectors.toList());
        response.setTargetIds(targetIds);
        response.setComplianceApproved(complianceReviewService.isApproved("Notification", notification.getId()));

        return response;
    }

    @SuppressWarnings("unchecked")
    private NotificationResponse mapFromIdempotentResponse(Object stored) {
        // The idempotency service returns a deserialized Map; re-map to our DTO
        if (stored instanceof NotificationResponse nr) {
            return nr;
        }
        // Fallback: stored as LinkedHashMap from Jackson deserialization
        if (stored instanceof java.util.Map) {
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) stored;
            NotificationResponse response = new NotificationResponse();
            if (map.get("id") != null) {
                response.setId(((Number) map.get("id")).longValue());
            }
            response.setTitle((String) map.get("title"));
            response.setContent((String) map.get("content"));
            return response;
        }
        return new NotificationResponse();
    }
}
