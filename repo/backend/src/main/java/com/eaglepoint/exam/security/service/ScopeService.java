package com.eaglepoint.exam.security.service;

import com.eaglepoint.exam.notifications.model.Notification;
import com.eaglepoint.exam.notifications.model.NotificationTarget;
import com.eaglepoint.exam.notifications.model.NotificationTargetType;
import com.eaglepoint.exam.notifications.repository.NotificationTargetRepository;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.security.model.UserScopeAssignment;
import com.eaglepoint.exam.security.repository.UserScopeAssignmentRepository;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.enums.ScopeType;
import com.eaglepoint.exam.shared.exception.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
/**
 * Service that enforces organisational scope restrictions.
 * <p>
 * ADMIN users bypass all scope checks. STUDENT users may only access
 * their own data. Other roles are checked against their
 * {@code user_scope_assignments}.
 */
@Service
public class ScopeService {

    /**
     * Maps entity types to the {@link ScopeType} used to restrict access.
     */
    private static final Map<String, ScopeType> ENTITY_SCOPE_MAP = Map.of(
            "CAMPUS", ScopeType.CAMPUS,
            "GRADE", ScopeType.GRADE,
            "TERM", ScopeType.TERM,
            "CLASS", ScopeType.CLASS,
            "COURSE", ScopeType.COURSE,
            "ROSTER_ENTRY", ScopeType.CLASS,
            "ROOM", ScopeType.CAMPUS,
            "NOTIFICATION", ScopeType.GRADE
    );

    private final UserScopeAssignmentRepository scopeRepository;
    private final NotificationTargetRepository notificationTargetRepository;

    public ScopeService(UserScopeAssignmentRepository scopeRepository,
                        NotificationTargetRepository notificationTargetRepository) {
        this.scopeRepository = scopeRepository;
        this.notificationTargetRepository = notificationTargetRepository;
    }

    /**
     * Verifies that the user has scope access to the specified entity.
     *
     * @throws AccessDeniedException if the user does not have scope access
     */
    public void enforceScope(Long userId, Role role, String entityType, Long entityId) {
        if (role == Role.ADMIN) {
            return; // admin bypasses all scope checks
        }

        if (role == Role.STUDENT) {
            // Students can only access their own data -- the entityId must be their userId
            if (!userId.equals(entityId)) {
                throw new AccessDeniedException(
                        "Students may only access their own data");
            }
            return;
        }

        ScopeType requiredScope = ENTITY_SCOPE_MAP.get(entityType.toUpperCase());
        if (requiredScope == null) {
            // No scope mapping defined for this entity type; deny by default
            throw new AccessDeniedException(
                    "No scope mapping defined for entity type: " + entityType);
        }

        boolean hasScope = scopeRepository.existsByUserIdAndScopeTypeAndScopeId(
                userId, requiredScope, entityId);
        if (!hasScope) {
            throw new AccessDeniedException(
                    "User does not have " + requiredScope + " scope for entity " + entityId);
        }
    }

    /**
     * Returns scope filter criteria for the given user and entity type.
     * The returned list contains the scope assignments relevant to the entity type,
     * which callers can use to build query filters.
     *
     * @return list of scope assignments applicable to the entity type, or empty
     *         list if the user has unrestricted access (ADMIN)
     */
    public List<UserScopeAssignment> filterByUserScope(Long userId, Role role, String entityType) {
        if (role == Role.ADMIN) {
            return Collections.emptyList(); // no filtering needed
        }

        ScopeType requiredScope = ENTITY_SCOPE_MAP.get(entityType.toUpperCase());
        if (requiredScope == null) {
            return Collections.emptyList();
        }

        return scopeRepository.findByUserIdAndScopeType(userId, requiredScope);
    }

    /**
     * Returns all scope assignments for a user.
     */
    public List<UserScopeAssignment> getScopeAssignments(Long userId) {
        return scopeRepository.findByUserId(userId);
    }

    /**
     * Distinct entity IDs the user holds for the given scope type (e.g. grade or class IDs).
     * Returns an empty list for {@link Role#ADMIN} and {@link Role#STUDENT}.
     */
    public List<Long> listScopeIds(Long userId, Role role, ScopeType scopeType) {
        if (role == Role.ADMIN || role == Role.STUDENT) {
            return Collections.emptyList();
        }
        return scopeRepository.findByUserIdAndScopeType(userId, scopeType).stream()
                .map(UserScopeAssignment::getScopeId)
                .distinct()
                .toList();
    }

    /**
     * Exam sessions are visible if the user holds any assignment matching campus, term,
     * course, or one of the session's classes (OR semantics).
     */
    public boolean hasExamSessionScope(Long userId, Role role,
                                       Long campusId, Long termId, Long courseId, List<Long> classIds) {
        if (role == Role.ADMIN) {
            return true;
        }
        if (role == Role.STUDENT) {
            return false;
        }
        if (campusId != null
                && scopeRepository.existsByUserIdAndScopeTypeAndScopeId(userId, ScopeType.CAMPUS, campusId)) {
            return true;
        }
        if (termId != null
                && scopeRepository.existsByUserIdAndScopeTypeAndScopeId(userId, ScopeType.TERM, termId)) {
            return true;
        }
        if (courseId != null
                && scopeRepository.existsByUserIdAndScopeTypeAndScopeId(userId, ScopeType.COURSE, courseId)) {
            return true;
        }
        if (classIds != null) {
            for (Long cid : classIds) {
                if (cid != null
                        && scopeRepository.existsByUserIdAndScopeTypeAndScopeId(userId, ScopeType.CLASS, cid)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void enforceExamSessionScope(Long userId, Role role,
                                        Long campusId, Long termId, Long courseId, List<Long> classIds) {
        if (role == Role.ADMIN) {
            return;
        }
        if (role == Role.STUDENT) {
            throw new AccessDeniedException("Students cannot perform this operation on exam sessions");
        }
        if (hasExamSessionScope(userId, role, campusId, termId, courseId, classIds)) {
            return;
        }
        throw new AccessDeniedException(
                "User does not have campus, term, course, or class scope for this exam session");
    }

    /**
     * Versioning access for notifications mirrors notification object-level rules (creator or scoped targets).
     */
    public void enforceNotificationEntityAccess(Notification notification) {
        Long uid = RequestContext.getUserId();
        Role role = RequestContext.getRole();
        if (role == Role.ADMIN) {
            return;
        }
        if (notification.getCreatedBy() != null && notification.getCreatedBy().equals(uid)) {
            return;
        }

        List<NotificationTarget> targets = notificationTargetRepository.findByNotificationId(notification.getId());
        NotificationTargetType targetType = notification.getTargetType();
        if (targets.isEmpty() || targetType == null) {
            throw new AccessDeniedException("You do not have access to this notification");
        }

        switch (targetType) {
            case ALL_STUDENTS -> throw new AccessDeniedException(
                    "Only the creator or an administrator can access this notification");
            case GRADE -> {
                for (NotificationTarget t : targets) {
                    enforceScope(uid, role, "NOTIFICATION", t.getTargetId());
                }
            }
            case CLASS -> {
                for (NotificationTarget t : targets) {
                    enforceScope(uid, role, "CLASS", t.getTargetId());
                }
            }
            case INDIVIDUAL -> throw new AccessDeniedException(
                    "Only the creator or an administrator can access individually targeted notifications");
            default -> throw new AccessDeniedException("Unsupported notification target type");
        }
    }
}
