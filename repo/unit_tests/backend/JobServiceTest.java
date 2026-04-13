package com.eaglepoint.exam.jobs.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.jobs.model.JobRun;
import com.eaglepoint.exam.jobs.model.JobRunStatus;
import com.eaglepoint.exam.jobs.repository.JobRunRepository;
import com.eaglepoint.exam.security.service.IdempotencyService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.StateTransitionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JobService} covering job enqueueing, deduplication,
 * retry with exponential backoff, max retries, manual rerun, and cancellation.
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRunRepository jobRunRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private JobService jobService;

    @BeforeEach
    void setUp() {
        RequestContext.set(1L, "admin1", Role.ADMIN, "session-1", "127.0.0.1", "trace-1");
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private JobRun createJobRun(Long id, JobRunStatus status, int attempts, int maxAttempts) {
        JobRun job = new JobRun();
        ReflectionTestUtils.setField(job, "id", id);
        job.setJobType("NOTIFICATION_SEND");
        job.setEntityId(100L);
        job.setStatus(status);
        job.setAttempts(attempts);
        job.setMaxAttempts(maxAttempts);
        job.setCreatedBy(1L);
        return job;
    }

    @Test
    void testEnqueueJob() {
        when(jobRunRepository.findByDedupKey("dedup-1")).thenReturn(Optional.empty());
        when(jobRunRepository.save(any(JobRun.class))).thenAnswer(inv -> {
            JobRun j = inv.getArgument(0);
            ReflectionTestUtils.setField(j, "id", 1L);
            return j;
        });

        JobRun job = jobService.enqueueJob("NOTIFICATION_SEND", 100L, "dedup-1", 1L);

        assertNotNull(job);
        assertEquals(JobRunStatus.QUEUED, job.getStatus());
        assertEquals("NOTIFICATION_SEND", job.getJobType());
        assertEquals("dedup-1", job.getDedupKey());
        verify(auditService).logAction(eq("ENQUEUE_JOB"), eq("JobRun"), eq(1L), isNull(), isNull(), anyString());
    }

    @Test
    void testDedupKeyPreventsDouble() {
        JobRun existing = createJobRun(1L, JobRunStatus.QUEUED, 0, 3);
        existing.setDedupKey("dedup-1");
        when(jobRunRepository.findByDedupKey("dedup-1")).thenReturn(Optional.of(existing));

        JobRun result = jobService.enqueueJob("NOTIFICATION_SEND", 100L, "dedup-1", 1L);

        // Should return existing job, not create a new one
        assertEquals(1L, result.getId());
        verify(jobRunRepository, never()).save(any(JobRun.class));
    }

    @Test
    void testRetryWithExponentialBackoff() {
        // Attempt 1 failed -> backoff = 4^(1-1) = 1 minute
        JobRun job1 = createJobRun(1L, JobRunStatus.RUNNING, 1, 3);
        job1.setFailureReason("Connection timeout");
        jobService.retryJob(job1);
        assertNotNull(job1.getNextRetryAt());
        // backoff = 4^0 = 1 minute
        assertTrue(job1.getNextRetryAt().isAfter(LocalDateTime.now()));
        assertTrue(job1.getNextRetryAt().isBefore(LocalDateTime.now().plusMinutes(2)));

        // Attempt 2 failed -> backoff = 4^(2-1) = 4 minutes
        JobRun job2 = createJobRun(2L, JobRunStatus.RUNNING, 2, 3);
        job2.setFailureReason("Connection timeout");
        jobService.retryJob(job2);
        assertNotNull(job2.getNextRetryAt());
        assertTrue(job2.getNextRetryAt().isAfter(LocalDateTime.now().plusMinutes(3)));
        assertTrue(job2.getNextRetryAt().isBefore(LocalDateTime.now().plusMinutes(5)));
    }

    @Test
    void testMaxRetriesExceeded() {
        // 3 failures (attempts == maxAttempts) -> permanently FAILED
        JobRun job = createJobRun(1L, JobRunStatus.RUNNING, 3, 3);
        job.setFailureReason("Persistent failure");

        jobService.retryJob(job);

        assertEquals(JobRunStatus.FAILED, job.getStatus());
        assertNotNull(job.getCompletedAt());
        verify(jobRunRepository).save(job);
    }

    @Test
    void testManualRerun() {
        JobRun originalJob = createJobRun(1L, JobRunStatus.FAILED, 3, 3);

        when(jobRunRepository.findById(1L)).thenReturn(Optional.of(originalJob));
        when(jobRunRepository.save(any(JobRun.class))).thenAnswer(inv -> {
            JobRun j = inv.getArgument(0);
            if (j.getId() == null) {
                ReflectionTestUtils.setField(j, "id", 2L);
            }
            return j;
        });

        JobRun rerunJob = jobService.rerunJob(1L, null);

        assertNotNull(rerunJob);
        assertEquals(JobRunStatus.QUEUED, rerunJob.getStatus());
        assertEquals(0, rerunJob.getAttempts());
        assertNull(rerunJob.getDedupKey());
        assertEquals(JobRunStatus.MANUALLY_RERUN, originalJob.getStatus());
    }

    @Test
    void testCancelQueuedJob() {
        JobRun job = createJobRun(1L, JobRunStatus.QUEUED, 0, 3);
        when(jobRunRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobRunRepository.save(any(JobRun.class))).thenAnswer(inv -> inv.getArgument(0));

        jobService.cancelJob(1L);

        assertEquals(JobRunStatus.CANCELED, job.getStatus());
        assertNotNull(job.getCompletedAt());
        verify(auditService).logAction(eq("CANCEL_JOB"), eq("JobRun"), eq(1L), isNull(), isNull(), eq("Canceled job"));
    }

    @Test
    void testCancelRunningJobFails() {
        JobRun job = createJobRun(1L, JobRunStatus.RUNNING, 1, 3);
        when(jobRunRepository.findById(1L)).thenReturn(Optional.of(job));

        assertThrows(StateTransitionException.class, () -> jobService.cancelJob(1L));
    }

    @Test
    void testIdempotentRerun() {
        when(idempotencyService.checkAndStore("rerun-key-1", 1L, "RERUN_JOB"))
                .thenReturn(new Object()); // indicates duplicate

        JobRun result = jobService.rerunJob(1L, "rerun-key-1");

        assertNull(result);
        verify(jobRunRepository, never()).findById(anyLong());
    }
}
