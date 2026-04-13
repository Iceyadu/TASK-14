package com.eaglepoint.exam.scheduling.model;

/**
 * Lifecycle states for an exam session.
 */
public enum ExamSessionStatus {
    DRAFT,
    SUBMITTED_FOR_COMPLIANCE_REVIEW,
    APPROVED,
    REJECTED,
    PUBLISHED,
    UNPUBLISHED,
    ARCHIVED,
    RESTORED
}
