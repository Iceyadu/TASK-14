package com.eaglepoint.exam.jobs.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.jobs.model.JobRun;
import com.eaglepoint.exam.jobs.model.JobRunStatus;
import com.eaglepoint.exam.jobs.repository.JobRunRepository;
import com.eaglepoint.exam.notifications.service.NotificationDeliveryService;
import com.eaglepoint.exam.security.service.IdempotencyService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import com.eaglepoint.exam.shared.exception.StateTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Value;

import java.util.stream.IntStream;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service managing background job lifecycle: enqueue, process, retry, rerun, cancel.
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    /** Total number of shards across the cluster. */
    public static final int TOTAL_SHARDS = 4;

    private final JobRunRepository jobRunRepository;
    private final NotificationDeliveryService notificationDeliveryService;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;

    @Value("${app.job.node-id:node-1}")
    private String nodeId;

    /** When true (e.g. single-node integration tests), poll all shards so every queued job can run. */
    @Value("${app.job.process-all-shards:false}")
    private boolean processAllShards;

    public JobService(JobRunRepository jobRunRepository,
                      NotificationDeliveryService notificationDeliveryService,
                      IdempotencyService idempotencyService,
                      AuditService auditService) {
        this.jobRunRepository = jobRunRepository;
        this.notificationDeliveryService = notificationDeliveryService;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
    }

    /**
     * Enqueues a new job, checking for deduplication.
     */
    @Transactional
    public JobRun enqueueJob(String jobType, Long entityId, String dedupKey, Long createdBy) {
        // Check dedup
        if (dedupKey != null) {
            Optional<JobRun> existing = jobRunRepository.findByDedupKey(dedupKey);
            if (existing.isPresent()) {
                JobRun existingJob = existing.get();
                if (existingJob.getStatus() != JobRunStatus.FAILED
                        && existingJob.getStatus() != JobRunStatus.CANCELED) {
                    log.info("Duplicate job detected for dedupKey={}, returning existing job #{}",
                            dedupKey, existingJob.getId());
                    return existingJob;
                }
            }
        }

        JobRun job = new JobRun();
        job.setJobType(jobType);
        job.setEntityId(entityId);
        job.setDedupKey(dedupKey);
        job.setStatus(JobRunStatus.QUEUED);
        job.setAttempts(0);
        job.setMaxAttempts(3);
        job.setCreatedBy(createdBy);

        // Assign shard key based on entity ID for consistent partitioning
        int shard = entityId != null ? (int) (Math.abs(entityId) % TOTAL_SHARDS) : 0;
        job.setShardKey(shard);

        JobRun saved = jobRunRepository.save(job);

        auditService.logAction("ENQUEUE_JOB", "JobRun", saved.getId(),
                null, null,
                "Enqueued job type=" + jobType + " entity=" + entityId);

        return saved;
    }

    /**
     * Returns the shard keys this node is responsible for, based on node ID hash.
     * Each node owns a subset of shards for distributed processing.
     */
    public List<Integer> getAssignedShards() {
        if (processAllShards) {
            return IntStream.range(0, TOTAL_SHARDS).boxed().toList();
        }
        int nodeHash = Math.abs(nodeId.hashCode());
        // Each node owns one primary shard and always includes shard 0 as fallback
        int primaryShard = nodeHash % TOTAL_SHARDS;
        if (primaryShard == 0) {
            return List.of(0);
        }
        return List.of(primaryShard, 0);
    }

    /**
     * Processes the next queued job for this node's assigned shards,
     * using SELECT FOR UPDATE SKIP LOCKED for distributed safety.
     */
    @Transactional
    public void processNextJob() {
        List<Integer> shards = getAssignedShards();
        Optional<JobRun> nextJob = jobRunRepository.findNextQueuedJobForShards(shards);
        if (nextJob.isEmpty()) {
            return;
        }

        JobRun job = nextJob.get();
        job.setStatus(JobRunStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        job.setAttempts(job.getAttempts() + 1);

        try {
            job.setNodeId(InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            job.setNodeId("unknown");
        }

        jobRunRepository.save(job);

        try {
            executeJob(job);
            job.setStatus(JobRunStatus.SUCCEEDED);
            job.setCompletedAt(LocalDateTime.now());
            jobRunRepository.save(job);
            log.info("Job #{} ({}) succeeded", job.getId(), job.getJobType());
        } catch (Exception e) {
            log.error("Job #{} ({}) failed: {}", job.getId(), job.getJobType(), e.getMessage(), e);
            job.setFailureReason(e.getMessage());
            retryJob(job);
        }
    }

    /**
     * Dispatches job execution to the appropriate service based on job type.
     */
    public void executeJob(JobRun job) {
        switch (job.getJobType()) {
            case "NOTIFICATION_SEND" -> {
                if (job.getEntityId() == null) {
                    throw new IllegalArgumentException("NOTIFICATION_SEND job requires entityId");
                }
                notificationDeliveryService.deliverNotification(job.getEntityId());
            }
            default -> throw new IllegalArgumentException("Unknown job type: " + job.getJobType());
        }
    }

    /**
     * Handles retry logic with exponential backoff (1, 4, 16 minutes).
     */
    public void retryJob(JobRun job) {
        if (job.getAttempts() < job.getMaxAttempts()) {
            job.setStatus(JobRunStatus.RETRYING);

            // Exponential backoff: 1 min, 4 min, 16 min (4^(attempt-1) minutes)
            long backoffMinutes = (long) Math.pow(4, job.getAttempts() - 1);
            job.setNextRetryAt(LocalDateTime.now().plusMinutes(backoffMinutes));

            // Re-queue for retry
            job.setStatus(JobRunStatus.QUEUED);
            log.info("Job #{} scheduled for retry in {} minutes (attempt {}/{})",
                    job.getId(), backoffMinutes, job.getAttempts(), job.getMaxAttempts());
        } else {
            job.setStatus(JobRunStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            log.warn("Job #{} permanently failed after {} attempts", job.getId(), job.getAttempts());
        }
        jobRunRepository.save(job);
    }

    /**
     * Creates a new QUEUED job as a copy of a failed/completed job.
     */
    @Transactional
    public JobRun rerunJob(Long jobId, String idempotencyKey) {
        Long userId = RequestContext.getUserId();

        if (idempotencyKey != null) {
            Object existing = idempotencyService.checkAndStore(idempotencyKey, userId, "RERUN_JOB");
            if (existing != null) {
                log.info("Idempotent duplicate detected for RERUN_JOB key={}", idempotencyKey);
                return null;
            }
        }

        JobRun original = jobRunRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("JobRun", jobId));

        JobRun newJob = new JobRun();
        newJob.setJobType(original.getJobType());
        newJob.setEntityId(original.getEntityId());
        newJob.setDedupKey(null); // new dedup for rerun
        newJob.setStatus(JobRunStatus.QUEUED);
        newJob.setAttempts(0);
        newJob.setMaxAttempts(original.getMaxAttempts());
        newJob.setCreatedBy(userId);

        JobRun saved = jobRunRepository.save(newJob);

        // Mark original as manually rerun
        original.setStatus(JobRunStatus.MANUALLY_RERUN);
        jobRunRepository.save(original);

        auditService.logAction("RERUN_JOB", "JobRun", saved.getId(),
                null, null,
                "Rerun of job #" + jobId + ", new job #" + saved.getId());

        if (idempotencyKey != null) {
            idempotencyService.storeResponse(idempotencyKey, userId, "RERUN_JOB", saved);
        }

        return saved;
    }

    /**
     * Cancels a QUEUED job.
     */
    @Transactional
    public void cancelJob(Long jobId) {
        JobRun job = jobRunRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("JobRun", jobId));

        if (job.getStatus() != JobRunStatus.QUEUED) {
            throw new StateTransitionException(
                    job.getStatus().name(), JobRunStatus.CANCELED.name(),
                    "Only QUEUED jobs can be canceled");
        }

        job.setStatus(JobRunStatus.CANCELED);
        job.setCompletedAt(LocalDateTime.now());
        jobRunRepository.save(job);

        auditService.logAction("CANCEL_JOB", "JobRun", jobId,
                null, null, "Canceled job");
    }

    /**
     * Lists jobs with optional status and job type filters, paginated.
     */
    @Transactional(readOnly = true)
    public Page<JobRun> listJobs(JobRunStatus status, String jobType, Pageable pageable) {
        if (status != null && jobType != null) {
            return jobRunRepository.findByStatusAndJobType(status, jobType, pageable);
        } else if (status != null) {
            return jobRunRepository.findByStatus(status, pageable);
        } else if (jobType != null) {
            return jobRunRepository.findByJobType(jobType, pageable);
        }
        return jobRunRepository.findAll(pageable);
    }

    /**
     * Returns a single job by ID.
     */
    @Transactional(readOnly = true)
    public JobRun getJob(Long id) {
        return jobRunRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("JobRun", id));
    }
}
