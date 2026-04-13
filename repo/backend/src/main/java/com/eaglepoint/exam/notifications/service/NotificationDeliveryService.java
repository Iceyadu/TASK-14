package com.eaglepoint.exam.notifications.service;

import com.eaglepoint.exam.notifications.model.DeliveryStatusEntry;
import com.eaglepoint.exam.notifications.model.DndSetting;
import com.eaglepoint.exam.notifications.model.InboxMessage;
import com.eaglepoint.exam.notifications.model.Notification;
import com.eaglepoint.exam.notifications.model.NotificationStatus;
import com.eaglepoint.exam.notifications.model.NotificationTarget;
import com.eaglepoint.exam.notifications.model.SubscriptionSetting;
import com.eaglepoint.exam.notifications.repository.DeliveryStatusRepository;
import com.eaglepoint.exam.notifications.repository.DndSettingRepository;
import com.eaglepoint.exam.notifications.repository.InboxMessageRepository;
import com.eaglepoint.exam.notifications.repository.NotificationRepository;
import com.eaglepoint.exam.notifications.repository.NotificationTargetRepository;
import com.eaglepoint.exam.notifications.repository.SubscriptionSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles the actual delivery of notifications to target students via
 * WeChat and in-app inbox channels.
 */
@Service
public class NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);
    private static final int MAX_WECHAT_RETRIES = 3;

    /**
     * {@code disabled} — skip WeChat and use inbox fallback.<br>
     * {@code simulated} — log and treat as delivered (no external call).<br>
     * {@code http} — POST JSON payload to {@code app.wechat.http.endpoint}.<br>
     * Legacy {@code app.wechat.enabled=true} maps to {@code simulated} when mode is unset.
     */
    @Value("${app.wechat.mode:}")
    private String wechatMode;

    @Value("${app.wechat.enabled:false}")
    private boolean wechatEnabledLegacy;

    @Value("${app.wechat.http.endpoint:}")
    private String wechatHttpEndpoint;

    private final NotificationRepository notificationRepository;
    private final NotificationTargetRepository targetRepository;
    private final DeliveryStatusRepository deliveryStatusRepository;
    private final InboxMessageRepository inboxMessageRepository;
    private final SubscriptionSettingRepository subscriptionSettingRepository;
    private final DndSettingRepository dndSettingRepository;

    public NotificationDeliveryService(NotificationRepository notificationRepository,
                                       NotificationTargetRepository targetRepository,
                                       DeliveryStatusRepository deliveryStatusRepository,
                                       InboxMessageRepository inboxMessageRepository,
                                       SubscriptionSettingRepository subscriptionSettingRepository,
                                       DndSettingRepository dndSettingRepository) {
        this.notificationRepository = notificationRepository;
        this.targetRepository = targetRepository;
        this.deliveryStatusRepository = deliveryStatusRepository;
        this.inboxMessageRepository = inboxMessageRepository;
        this.subscriptionSettingRepository = subscriptionSettingRepository;
        this.dndSettingRepository = dndSettingRepository;
    }

    /**
     * Delivers a notification to all its target students.
     * For each target:
     * 1. Check subscription opt-out
     * 2. Check DND window (deliver to inbox only if active)
     * 3. Attempt WeChat delivery; on failure, fall back to inbox
     * 4. Track delivery status per channel
     */
    @Transactional
    public void deliverNotification(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));

        // Transition to SENDING
        notification.setStatus(NotificationStatus.SENDING);
        notificationRepository.save(notification);

        List<NotificationTarget> targets = targetRepository.findByNotificationId(notificationId);
        boolean allDelivered = true;
        boolean anyDelivered = false;

        for (NotificationTarget target : targets) {
            Long studentUserId = target.getTargetId();

            // Check subscription opt-out
            if (isOptedOut(studentUserId, notification.getEventType().name())) {
                log.info("Student {} opted out of event type {}, skipping",
                        studentUserId, notification.getEventType());
                trackDelivery(notificationId, studentUserId, "SKIPPED", "opted_out",
                        null, null);
                continue;
            }

            // Check DND window
            if (isDndActive(studentUserId)) {
                log.info("Student {} is in DND window, delivering to inbox only with held flag",
                        studentUserId);
                createInboxMessage(studentUserId, notification);
                trackDelivery(notificationId, studentUserId, "IN_APP", "delivered_dnd_held",
                        LocalDateTime.now(), null);
                anyDelivered = true;
                continue;
            }

            // Attempt WeChat delivery with retries
            boolean wechatSuccess = false;
            String failureReason = null;
            for (int attempt = 1; attempt <= MAX_WECHAT_RETRIES; attempt++) {
                try {
                    wechatSuccess = attemptWeChatDelivery(studentUserId, notification);
                    if (wechatSuccess) {
                        break;
                    }
                    failureReason = "WeChat delivery failed on attempt " + attempt;
                } catch (Exception e) {
                    failureReason = "WeChat error on attempt " + attempt + ": " + e.getMessage();
                    log.warn("WeChat delivery attempt {} failed for student {}: {}",
                            attempt, studentUserId, e.getMessage());
                }
            }

            if (wechatSuccess) {
                trackDelivery(notificationId, studentUserId, "WECHAT", "delivered",
                        LocalDateTime.now(), null);
                anyDelivered = true;
            } else {
                // Fallback to in-app inbox
                log.info("WeChat failed for student {}, falling back to in-app inbox", studentUserId);
                createInboxMessage(studentUserId, notification);
                trackDelivery(notificationId, studentUserId, "IN_APP", "fallback_delivered",
                        LocalDateTime.now(), failureReason);
                anyDelivered = true;
                allDelivered = false;
            }
        }

        // Update notification status
        if (targets.isEmpty() || !anyDelivered) {
            notification.setStatus(NotificationStatus.FAILED);
        } else if (allDelivered) {
            notification.setStatus(NotificationStatus.DELIVERED);
        } else {
            notification.setStatus(NotificationStatus.FALLBACK_TO_IN_APP);
        }
        notificationRepository.save(notification);
    }

    /**
     * Checks whether the student's DND window is currently active.
     */
    public boolean isDndActive(Long studentUserId) {
        Optional<DndSetting> dndOpt = dndSettingRepository.findByStudentUserId(studentUserId);
        if (dndOpt.isEmpty()) {
            return false;
        }

        DndSetting dnd = dndOpt.get();
        if (dnd.getDndStart() == null || dnd.getDndEnd() == null) {
            return false;
        }

        LocalTime now = LocalTime.now();
        LocalTime start = dnd.getDndStart();
        LocalTime end = dnd.getDndEnd();

        // Handle overnight DND windows (e.g., 22:00 - 07:00)
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        } else {
            // Crosses midnight
            return !now.isBefore(start) || now.isBefore(end);
        }
    }

    /**
     * Creates an in-app inbox message for the student from the given notification.
     */
    public void createInboxMessage(Long studentUserId, Notification notification) {
        InboxMessage inbox = new InboxMessage();
        inbox.setStudentUserId(studentUserId);
        inbox.setNotificationId(notification.getId());
        inbox.setTitle(notification.getTitle());
        inbox.setContent(notification.getContent());
        inbox.setIsRead(false);
        inboxMessageRepository.save(inbox);
    }

    /**
     * Attempts to deliver a notification via WeChat.
     * Returns true on success, false on failure.
     * If WeChat is not enabled, immediately returns false to trigger fallback.
     */
    public boolean attemptWeChatDelivery(Long studentUserId, Notification notification) {
        String mode = resolvedWechatMode();
        return switch (mode) {
            case "disabled", "none" -> {
                log.debug("WeChat mode is {}, using inbox fallback path", mode);
                yield false;
            }
            case "simulated" -> {
                log.info("WeChat simulated delivery to student {} — title: {}",
                        studentUserId, notification.getTitle());
                yield true;
            }
            case "http" -> deliverWeChatHttp(studentUserId, notification);
            default -> {
                log.warn("Unknown app.wechat.mode '{}', treating as disabled", wechatMode);
                yield false;
            }
        };
    }

    private String resolvedWechatMode() {
        if (wechatMode != null && !wechatMode.isBlank()) {
            return wechatMode.trim().toLowerCase();
        }
        return wechatEnabledLegacy ? "simulated" : "disabled";
    }

    private boolean deliverWeChatHttp(Long studentUserId, Notification notification) {
        if (wechatHttpEndpoint == null || wechatHttpEndpoint.isBlank()) {
            log.warn("WeChat http mode is active but app.wechat.http.endpoint is empty");
            return false;
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> body = new HashMap<>();
            body.put("studentUserId", studentUserId);
            body.put("notificationId", notification.getId());
            body.put("title", notification.getTitle());
            var response = restTemplate.postForEntity(wechatHttpEndpoint, body, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("WeChat HTTP delivery failed: {}", e.getMessage());
            return false;
        }
    }

    // ---- Private helpers ----

    private boolean isOptedOut(Long studentUserId, String eventType) {
        Optional<SubscriptionSetting> setting = subscriptionSettingRepository
                .findByStudentUserIdAndEventType(studentUserId, eventType);
        return setting.isPresent() && !setting.get().isEnabled();
    }

    private void trackDelivery(Long notificationId, Long studentUserId, String channel,
                               String status, LocalDateTime deliveredAt, String failureReason) {
        DeliveryStatusEntry entry = new DeliveryStatusEntry();
        entry.setNotificationId(notificationId);
        entry.setStudentUserId(studentUserId);
        entry.setChannel(channel);
        entry.setStatus(status);
        entry.setAttemptedAt(LocalDateTime.now());
        entry.setDeliveredAt(deliveredAt);
        entry.setFailureReason(failureReason);
        deliveryStatusRepository.save(entry);
    }
}
