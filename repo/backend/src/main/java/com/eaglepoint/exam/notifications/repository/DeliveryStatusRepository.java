package com.eaglepoint.exam.notifications.repository;

import com.eaglepoint.exam.notifications.model.DeliveryStatusEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA repository for {@link DeliveryStatusEntry}.
 */
@Repository
public interface DeliveryStatusRepository extends JpaRepository<DeliveryStatusEntry, Long> {

    List<DeliveryStatusEntry> findByNotificationId(Long notificationId);

    List<DeliveryStatusEntry> findByStudentUserId(Long studentUserId);
}
