package com.eaglepoint.exam.imports.model;

/**
 * Lifecycle status values for an import job.
 */
public enum ImportJobStatus {
    UPLOADED,
    PREVIEWED,
    VALIDATION_FAILED,
    PARTIALLY_VALID,
    APPROVED_FOR_COMMIT,
    COMMITTED,
    ROLLED_BACK
}
