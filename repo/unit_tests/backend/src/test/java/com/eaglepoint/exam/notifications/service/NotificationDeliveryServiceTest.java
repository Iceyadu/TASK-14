package com.eaglepoint.exam.notifications.service;

import com.eaglepoint.exam.notifications.model.DeliveryStatusEntry;
import com.eaglepoint.exam.notifications.model.DndSetting;
import com.eaglepoint.exam.notifications.model.Notification;
import com.eaglepoint.exam.notifications.model.NotificationEventType;
import com.eaglepoint.exam.notifications.model.NotificationStatus;
import com.eaglepoint.exam.notifications.model.NotificationTarget;
import com.eaglepoint.exam.notifications.model.SubscriptionSetting;
import com.eaglepoint.exam.notifications.repository.DeliveryStatusRepository;
import com.eaglepoint.exam.notifications.repository.DndSettingRepository;
import com.eaglepoint.exam.notifications.repository.InboxMessageRepository;
import com.eaglepoint.exam.notifications.repository.NotificationRepository;
import com.eaglepoint.exam.notifications.repository.NotificationTargetRepository;
import com.eaglepoint.exam.notifications.repository.SubscriptionSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationTargetRepository targetRepository;
    @Mock private DeliveryStatusRepository deliveryStatusRepository;
    @Mock private InboxMessageRepository inboxMessageRepository;
    @Mock private SubscriptionSettingRepository subscriptionSettingRepository;
    @Mock private DndSettingRepository dndSettingRepository;

    private NotificationDeliveryService newService() {
        NotificationDeliveryService service = new NotificationDeliveryService(
                notificationRepository, targetRepository, deliveryStatusRepository,
                inboxMessageRepository, subscriptionSettingRepository, dndSettingRepository);
        ReflectionTestUtils.setField(service, "wechatMode", "disabled");
        ReflectionTestUtils.setField(service, "wechatEnabledLegacy", false);
        return service;
    }

    private Notification sampleNotification(Long id) {
        Notification n = new Notification();
        n.setId(id);
        n.setTitle("Exam reminder");
        n.setContent("Bring ID");
        n.setStatus(NotificationStatus.QUEUED);
        n.setEventType(NotificationEventType.CHECK_IN_REMINDER);
        return n;
    }

    private NotificationTarget target(Long targetId) {
        NotificationTarget t = new NotificationTarget();
        t.setTargetId(targetId);
        return t;
    }

    // ---- WeChat disabled falls back to in-app ----

    @Test
    void testFallbackToInboxWhenWeChatDisabled() {
        Notification n = sampleNotification(101L);
        when(notificationRepository.findById(101L)).thenReturn(Optional.of(n));
        when(targetRepository.findByNotificationId(101L)).thenReturn(List.of(target(55L)));
        when(subscriptionSettingRepository.findByStudentUserIdAndEventType(55L, "CHECK_IN_REMINDER"))
                .thenReturn(Optional.empty());
        when(dndSettingRepository.findByStudentUserId(55L)).thenReturn(Optional.empty());

        newService().deliverNotification(101L);

        verify(inboxMessageRepository).save(any());
        ArgumentCaptor<DeliveryStatusEntry> cap = ArgumentCaptor.forClass(DeliveryStatusEntry.class);
        verify(deliveryStatusRepository, atLeastOnce()).save(cap.capture());
        assertTrue(cap.getAllValues().stream().anyMatch(e ->
                "IN_APP".equals(e.getChannel()) && "fallback_delivered".equals(e.getStatus())));
        assertEquals(NotificationStatus.FALLBACK_TO_IN_APP, n.getStatus());
    }

    // ---- DND holds delivery ----

    @Test
    void testDndDeliveryHeldStatus() {
        Notification n = sampleNotification(101L);
        DndSetting dnd = new DndSetting();
        dnd.setStudentUserId(88L);
        dnd.setDndStart(LocalTime.now().minusMinutes(1));
        dnd.setDndEnd(LocalTime.now().plusMinutes(1));

        when(notificationRepository.findById(101L)).thenReturn(Optional.of(n));
        when(targetRepository.findByNotificationId(101L)).thenReturn(List.of(target(88L)));
        when(subscriptionSettingRepository.findByStudentUserIdAndEventType(88L, "CHECK_IN_REMINDER"))
                .thenReturn(Optional.empty());
        when(dndSettingRepository.findByStudentUserId(88L)).thenReturn(Optional.of(dnd));

        newService().deliverNotification(101L);

        ArgumentCaptor<DeliveryStatusEntry> cap = ArgumentCaptor.forClass(DeliveryStatusEntry.class);
        verify(deliveryStatusRepository, atLeastOnce()).save(cap.capture());
        assertTrue(cap.getAllValues().stream().anyMatch(e ->
                "IN_APP".equals(e.getChannel()) && "delivered_dnd_held".equals(e.getStatus())));
        assertEquals(NotificationStatus.DELIVERED, n.getStatus());
    }

    // ---- opted-out is tracked as skipped ----

    @Test
    void testOptOutIsTrackedAsSkipped() {
        Notification n = sampleNotification(101L);
        SubscriptionSetting setting = new SubscriptionSetting();
        setting.setStudentUserId(99L);
        setting.setEventType("CHECK_IN_REMINDER");
        setting.setEnabled(false);

        when(notificationRepository.findById(101L)).thenReturn(Optional.of(n));
        when(targetRepository.findByNotificationId(101L)).thenReturn(List.of(target(99L)));
        when(subscriptionSettingRepository.findByStudentUserIdAndEventType(99L, "CHECK_IN_REMINDER"))
                .thenReturn(Optional.of(setting));

        newService().deliverNotification(101L);

        ArgumentCaptor<DeliveryStatusEntry> cap = ArgumentCaptor.forClass(DeliveryStatusEntry.class);
        verify(deliveryStatusRepository, atLeastOnce()).save(cap.capture());
        assertTrue(cap.getAllValues().stream().anyMatch(e ->
                "SKIPPED".equals(e.getChannel()) && "opted_out".equals(e.getStatus())));
        assertEquals(NotificationStatus.FAILED, n.getStatus());
    }

    // ---- multi-target delivery: each target processed independently ----

    @Test
    void testMultiTargetDeliveryProcessesEachTarget() {
        Notification n = sampleNotification(200L);
        when(notificationRepository.findById(200L)).thenReturn(Optional.of(n));
        when(targetRepository.findByNotificationId(200L))
                .thenReturn(List.of(target(11L), target(12L), target(13L)));
        when(subscriptionSettingRepository.findByStudentUserIdAndEventType(any(), any()))
                .thenReturn(Optional.empty());
        when(dndSettingRepository.findByStudentUserId(any())).thenReturn(Optional.empty());

        newService().deliverNotification(200L);

        verify(inboxMessageRepository, times(3)).save(any());
    }

    // ---- all opted-out => notification status FAILED ----

    @Test
    void testAllTargetsOptedOutMarksFailed() {
        Notification n = sampleNotification(300L);
        SubscriptionSetting optOut = new SubscriptionSetting();
        optOut.setStudentUserId(55L);
        optOut.setEventType("CHECK_IN_REMINDER");
        optOut.setEnabled(false);

        when(notificationRepository.findById(300L)).thenReturn(Optional.of(n));
        when(targetRepository.findByNotificationId(300L)).thenReturn(List.of(target(55L)));
        when(subscriptionSettingRepository.findByStudentUserIdAndEventType(55L, "CHECK_IN_REMINDER"))
                .thenReturn(Optional.of(optOut));

        newService().deliverNotification(300L);

        assertEquals(NotificationStatus.FAILED, n.getStatus());
    }
}
