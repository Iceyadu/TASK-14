package com.eaglepoint.exam.anticheat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for reviewing an anti-cheat flag.
 */
public class ReviewFlagRequest {

    @NotBlank(message = "Decision is required")
    private String decision;

    private String comment;

    public ReviewFlagRequest() {
    }

    // ---- Getters / Setters ----

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
