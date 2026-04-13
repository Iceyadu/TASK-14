package com.eaglepoint.exam.notifications.model;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle states for a notification with valid transition rules.
 */
public enum NotificationStatus {
    DRAFT,
    QUEUED,
    SENDING,
    DELIVERED,
    FAILED,
    RETRIED,
    CANCELED,
    EXPIRED,
    FALLBACK_TO_IN_APP;

    private static final Map<NotificationStatus, Set<NotificationStatus>> VALID_TRANSITIONS = Map.of(
            DRAFT, Set.of(QUEUED, CANCELED),
            QUEUED, Set.of(SENDING, CANCELED),
            SENDING, Set.of(DELIVERED, FAILED, FALLBACK_TO_IN_APP),
            FAILED, Set.of(RETRIED, FALLBACK_TO_IN_APP),
            RETRIED, Set.of(SENDING, FAILED),
            DELIVERED, Set.of(EXPIRED),
            CANCELED, Set.of(),
            EXPIRED, Set.of(),
            FALLBACK_TO_IN_APP, Set.of()
    );

    /**
     * Returns true if transitioning from this status to the target is valid.
     */
    public boolean canTransitionTo(NotificationStatus target) {
        Set<NotificationStatus> allowed = VALID_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    /**
     * Returns the set of valid transitions from this status.
     */
    public Set<NotificationStatus> validTransitions() {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of());
    }
}
