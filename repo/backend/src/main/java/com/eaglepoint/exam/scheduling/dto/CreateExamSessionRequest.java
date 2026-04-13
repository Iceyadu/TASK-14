package com.eaglepoint.exam.scheduling.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Request payload for creating or updating an exam session.
 */
public class CreateExamSessionRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Term ID is required")
    private Long termId;

    @NotNull(message = "Course ID is required")
    private Long courseId;

    @NotNull(message = "Campus ID is required")
    private Long campusId;

    @NotNull(message = "Exam date is required")
    @Future(message = "Exam date must be in the future")
    @JsonAlias("scheduledDate")
    private LocalDate examDate;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    @NotNull(message = "Room ID is required")
    private Long roomId;

    @NotEmpty(message = "At least one class ID is required")
    private List<Long> classIds;

    public CreateExamSessionRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getTermId() {
        return termId;
    }

    public void setTermId(Long termId) {
        this.termId = termId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getCampusId() {
        return campusId;
    }

    public void setCampusId(Long campusId) {
        this.campusId = campusId;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDate examDate) {
        this.examDate = examDate;
    }

    /** Alias used by tests and older API clients; maps to {@link #getExamDate()}. */
    public LocalDate getScheduledDate() {
        return examDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.examDate = scheduledDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public List<Long> getClassIds() {
        return classIds;
    }

    public void setClassIds(List<Long> classIds) {
        this.classIds = classIds;
    }
}
