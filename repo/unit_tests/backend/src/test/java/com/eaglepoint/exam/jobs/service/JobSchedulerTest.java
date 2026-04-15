package com.eaglepoint.exam.jobs.service;

import com.eaglepoint.exam.notifications.model.DndSetting;
import com.eaglepoint.exam.notifications.repository.DndSettingRepository;
import com.eaglepoint.exam.notifications.repository.InboxMessageRepository;
import com.eaglepoint.exam.security.repository.IdempotencyKeyRepository;
import com.eaglepoint.exam.security.repository.NonceReplayRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSchedulerTest {

    @Mock private JobService jobService;
    @Mock private NonceReplayRepository nonceReplayRepository;
    @Mock private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock private DndSettingRepository dndSettingRepository;
    @Mock private InboxMessageRepository inboxMessageRepository;

    private JobScheduler newScheduler() {
        return new JobScheduler(jobService, nonceReplayRepository, idempotencyKeyRepository,
                dndSettingRepository, inboxMessageRepository);
    }

    // ---- processJobs ----

    @Test
    void testProcessJobsDelegatesToJobService() {
        newScheduler().processJobs();
        verify(jobService).processNextJob();
    }

    // ---- cleanup ----

    @Test
    void testCleanupRepositoriesAreInvoked() {
        JobScheduler scheduler = newScheduler();
        scheduler.cleanupNonces();
        scheduler.cleanupIdempotencyKeys();

        verify(nonceReplayRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
        verify(idempotencyKeyRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }

    // ---- releaseDndNotifications ----

    @Test
    void testReleaseDndNotificationsReadsDndSettings() {
        when(dndSettingRepository.findAll()).thenReturn(List.of());

        newScheduler().releaseDndNotifications();

        verify(dndSettingRepository).findAll();
    }

    @Test
    void testReleaseDndNotificationsWithActiveDndWindowIteratesWithoutError() {
        DndSetting dnd = new DndSetting();
        dnd.setStudentUserId(10L);
        dnd.setDndStart(LocalTime.now().minusMinutes(10));
        dnd.setDndEnd(LocalTime.now().plusMinutes(10));

        when(dndSettingRepository.findAll()).thenReturn(List.of(dnd));

        // Should complete without throwing
        newScheduler().releaseDndNotifications();

        verify(dndSettingRepository).findAll();
    }

    @Test
    void testReleaseDndNotificationsWithNullWindowSkipsGracefully() {
        DndSetting dnd = new DndSetting();
        dnd.setStudentUserId(20L);
        dnd.setDndStart(null);
        dnd.setDndEnd(null);

        when(dndSettingRepository.findAll()).thenReturn(List.of(dnd));

        // Null DND window entries must be skipped without NullPointerException
        newScheduler().releaseDndNotifications();

        verify(dndSettingRepository).findAll();
    }

    @Test
    void testReleaseDndNotificationsWithExpiredWindowCompletesWithoutError() {
        DndSetting dnd = new DndSetting();
        dnd.setStudentUserId(30L);
        // DND window in past hours so it is not active now
        dnd.setDndStart(LocalTime.of(0, 0));
        dnd.setDndEnd(LocalTime.of(0, 1));

        when(dndSettingRepository.findAll()).thenReturn(List.of(dnd));

        newScheduler().releaseDndNotifications();

        verify(dndSettingRepository).findAll();
    }

    @Test
    void testReleaseDndNotificationsHandlesMultipleDndSettingsWithoutError() {
        DndSetting active = new DndSetting();
        active.setStudentUserId(1L);
        active.setDndStart(LocalTime.now().minusMinutes(5));
        active.setDndEnd(LocalTime.now().plusMinutes(5));

        DndSetting expired = new DndSetting();
        expired.setStudentUserId(2L);
        expired.setDndStart(LocalTime.of(1, 0));
        expired.setDndEnd(LocalTime.of(1, 1));

        DndSetting nullWindow = new DndSetting();
        nullWindow.setStudentUserId(3L);

        when(dndSettingRepository.findAll()).thenReturn(List.of(active, expired, nullWindow));

        newScheduler().releaseDndNotifications();

        verify(dndSettingRepository).findAll();
    }
}
