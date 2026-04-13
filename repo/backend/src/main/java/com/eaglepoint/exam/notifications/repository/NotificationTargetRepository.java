package com.eaglepoint.exam.notifications.repository;

import com.eaglepoint.exam.notifications.model.NotificationTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA repository for {@link NotificationTarget}.
 */
@Repository
public interface NotificationTargetRepository extends JpaRepository<NotificationTarget, Long> {

    List<NotificationTarget> findByNotificationId(Long notificationId);
}
