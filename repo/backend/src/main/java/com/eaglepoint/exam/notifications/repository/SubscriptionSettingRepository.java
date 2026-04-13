package com.eaglepoint.exam.notifications.repository;

import com.eaglepoint.exam.notifications.model.SubscriptionSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link SubscriptionSetting}.
 */
@Repository
public interface SubscriptionSettingRepository extends JpaRepository<SubscriptionSetting, Long> {

    List<SubscriptionSetting> findByStudentUserId(Long studentUserId);

    Optional<SubscriptionSetting> findByStudentUserIdAndEventType(Long studentUserId, String eventType);
}
