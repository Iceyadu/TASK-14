package com.eaglepoint.exam.jobs.repository;

import com.eaglepoint.exam.jobs.model.JobRun;
import com.eaglepoint.exam.jobs.model.JobRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for {@link JobRun} with pessimistic locking for job polling.
 */
@Repository
public interface JobRunRepository extends JpaRepository<JobRun, Long> {

    List<JobRun> findByStatus(JobRunStatus status);

    Optional<JobRun> findByDedupKey(String dedupKey);

    Page<JobRun> findByStatusAndJobType(JobRunStatus status, String jobType, Pageable pageable);

    Page<JobRun> findByStatus(JobRunStatus status, Pageable pageable);

    Page<JobRun> findByJobType(String jobType, Pageable pageable);

    /**
     * Picks the next queued job using SELECT FOR UPDATE SKIP LOCKED to prevent
     * concurrent processing by multiple nodes.
     */
    @Query(value = "SELECT * FROM job_runs WHERE status = 'QUEUED' " +
            "AND (next_retry_at IS NULL OR next_retry_at <= NOW()) " +
            "ORDER BY created_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    Optional<JobRun> findNextQueuedJob();

    /**
     * Picks the next queued job assigned to a specific shard (or the global shard 0).
     * Uses SELECT FOR UPDATE SKIP LOCKED for distributed node-safe polling.
     */
    @Query(value = "SELECT * FROM job_runs WHERE status = 'QUEUED' " +
            "AND shard_key IN (:shardKeys) " +
            "AND (next_retry_at IS NULL OR next_retry_at <= NOW()) " +
            "ORDER BY created_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    Optional<JobRun> findNextQueuedJobForShards(@org.springframework.data.repository.query.Param("shardKeys") List<Integer> shardKeys);
}
