package com.eaglepoint.exam.jobs.controller;

import com.eaglepoint.exam.jobs.model.JobRun;
import com.eaglepoint.exam.jobs.model.JobRunStatus;
import com.eaglepoint.exam.jobs.service.JobService;
import com.eaglepoint.exam.security.annotation.RequirePermission;
import com.eaglepoint.exam.shared.dto.ApiResponse;
import com.eaglepoint.exam.shared.dto.PaginationInfo;
import com.eaglepoint.exam.shared.enums.Permission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for background job monitoring and management.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Lists jobs with optional status and type filters, paginated.
     */
    @GetMapping
    @RequirePermission(Permission.JOB_MONITOR)
    public ResponseEntity<ApiResponse<List<JobRun>>> listJobs(
            @RequestParam(required = false) JobRunStatus status,
            @RequestParam(required = false) String jobType,
            Pageable pageable) {

        Page<JobRun> page = jobService.listJobs(status, jobType, pageable);

        PaginationInfo pagination = new PaginationInfo(
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(page.getContent(), pagination));
    }

    /**
     * Returns a single job by ID.
     */
    @GetMapping("/{id}")
    @RequirePermission(Permission.JOB_MONITOR)
    public ResponseEntity<ApiResponse<JobRun>> getJob(@PathVariable Long id) {
        JobRun job = jobService.getJob(id);
        return ResponseEntity.ok(ApiResponse.success(job));
    }

    /**
     * Reruns a failed or completed job by creating a new queued copy.
     */
    @PostMapping("/{id}/rerun")
    @RequirePermission(Permission.JOB_RERUN)
    public ResponseEntity<ApiResponse<JobRun>> rerunJob(
            @PathVariable Long id,
            @RequestParam(required = false) String idempotencyKey) {

        JobRun newJob = jobService.rerunJob(id, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success(newJob));
    }

    /**
     * Cancels a queued job.
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission(Permission.JOB_RERUN)
    public ResponseEntity<ApiResponse<Void>> cancelJob(@PathVariable Long id) {
        jobService.cancelJob(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
