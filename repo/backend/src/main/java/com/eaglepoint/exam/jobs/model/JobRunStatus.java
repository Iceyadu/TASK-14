package com.eaglepoint.exam.jobs.model;

/**
 * Lifecycle states for a background job run.
 */
public enum JobRunStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    RETRYING,
    MANUALLY_RERUN,
    CANCELED
}
