package com.eaglepoint.exam.compliance.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for approving or rejecting a compliance review.
 */
public class ReviewDecisionRequest {

    @NotBlank(message = "Comment is required")
    private String comment;

    private String requiredChanges;

    public ReviewDecisionRequest() {
    }

    // ---- Getters / Setters ----

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getRequiredChanges() {
        return requiredChanges;
    }

    public void setRequiredChanges(String requiredChanges) {
        this.requiredChanges = requiredChanges;
    }
}
