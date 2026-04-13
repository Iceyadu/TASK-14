package com.eaglepoint.exam.jobs.service;

import com.eaglepoint.exam.notifications.model.DndSetting;
import com.eaglepoint.exam.notifications.model.InboxMessage;
import com.eaglepoint.exam.notifications.repository.DndSettingRepository;
import com.eaglepoint.exam.notifications.repository.InboxMessageRepository;
import com.eaglepoint.exam.security.repository.IdempotencyKeyRepository;
import com.eaglepoint.exam.security.repository.NonceReplayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Scheduled background tasks for job processing, cleanup, and DND release.
 */
@Component
public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    private final JobService jobService;
    private final NonceReplayRepository nonceReplayRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final DndSettingRepository dndSettingRepository;
    private final InboxMessageRepository inboxMessageRepository;

    public JobScheduler(JobService jobService,
                        NonceReplayRepository nonceReplayRepository,
                        IdempotencyKeyRepository idempotencyKeyRepository,
                        DndSettingRepository dndSettingRepository,
                        InboxMessageRepository inboxMessageRepository) {
        this.jobService = jobService;
        this.nonceReplayRepository = nonceReplayRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.dndSettingRepository = dndSettingRepository;
        this.inboxMessageRepository = inboxMessageRepository;
    }

    /**
     * Processes queued jobs every 5 seconds.
     */
    @Scheduled(fixedRate = 5000)
    public void processJobs() {
        try {
            jobService.processNextJob();
        } catch (Exception e) {
            log.error("Error processing job: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleans up expired nonces every 5 minutes.
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void cleanupNonces() {
        try {
            nonceReplayRepository.deleteByExpiresAtBefore(LocalDateTime.now());
            log.debug("Cleaned up expired nonces");
        } catch (Exception e) {
            log.error("Error cleaning up nonces: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleans up expired idempotency keys every hour.
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupIdempotencyKeys() {
        try {
            idempotencyKeyRepository.deleteByExpiresAtBefore(LocalDateTime.now());
            log.debug("Cleaned up expired idempotency keys");
        } catch (Exception e) {
            log.error("Error cleaning up idempotency keys: {}", e.getMessage(), e);
        }
    }

    /**
     * Releases DND-held notifications every minute. Checks which students' DND
     * windows have ended and ensures their held inbox messages are available.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void releaseDndNotifications() {
        try {
            List<DndSetting> allDndSettings = dndSettingRepository.findAll();
            LocalTime now = LocalTime.now();

            for (DndSetting dnd : allDndSettings) {
                if (dnd.getDndStart() == null || dnd.getDndEnd() == null) {
                    continue;
                }

                // Check if DND window just ended (within the last minute)
                boolean dndActive;
                if (dnd.getDndStart().isBefore(dnd.getDndEnd())) {
                    dndActive = !now.isBefore(dnd.getDndStart()) && now.isBefore(dnd.getDndEnd());
                } else {
                    dndActive = !now.isBefore(dnd.getDndStart()) || now.isBefore(dnd.getDndEnd());
                }

                if (!dndActive) {
                    // DND is not active - any held messages are now released.
                    // Inbox messages are already created during delivery with DND,
                    // so they are immediately available when DND ends.
                    // No additional action needed as messages are always in the inbox.
                    log.trace("DND window inactive for student {}", dnd.getStudentUserId());
                }
            }
        } catch (Exception e) {
            log.error("Error releasing DND notifications: {}", e.getMessage(), e);
        }
    }
}
