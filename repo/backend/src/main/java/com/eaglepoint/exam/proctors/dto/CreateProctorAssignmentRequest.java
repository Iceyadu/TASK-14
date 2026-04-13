package com.eaglepoint.exam.proctors.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for creating a proctor assignment.
 */
public class CreateProctorAssignmentRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Exam session ID is required")
    private Long examSessionId;

    @NotNull(message = "Room ID is required")
    private Long roomId;

    public CreateProctorAssignmentRequest() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getExamSessionId() {
        return examSessionId;
    }

    public void setExamSessionId(Long examSessionId) {
        this.examSessionId = examSessionId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }
}
