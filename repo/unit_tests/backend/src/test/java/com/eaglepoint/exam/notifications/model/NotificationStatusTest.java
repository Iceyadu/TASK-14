package com.eaglepoint.exam.notifications.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationStatusTest {

    @Test
    void testFailedCanTransitionToRetriedButNotDirectlyToDelivered() {
        assertTrue(NotificationStatus.FAILED.canTransitionTo(NotificationStatus.RETRIED));
        assertFalse(NotificationStatus.FAILED.canTransitionTo(NotificationStatus.DELIVERED));
    }

    @Test
    void testDeliveredCanTransitionOnlyToExpired() {
        assertTrue(NotificationStatus.DELIVERED.canTransitionTo(NotificationStatus.EXPIRED));
        assertFalse(NotificationStatus.DELIVERED.canTransitionTo(NotificationStatus.RETRIED));
        assertFalse(NotificationStatus.DELIVERED.canTransitionTo(NotificationStatus.CANCELED));
    }
}
