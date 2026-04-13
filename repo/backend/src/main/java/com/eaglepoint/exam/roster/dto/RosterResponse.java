package com.eaglepoint.exam.roster.dto;

import com.eaglepoint.exam.security.masking.MaskedField;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.eaglepoint.exam.security.masking.MaskedFieldSerializer;

import java.time.LocalDateTime;

/**
 * Response DTO for a roster entry. Sensitive fields are masked by default
 * unless the requesting user has VIEW_HEALTH_DATA permission.
 */
public class RosterResponse {

    private Long id;
    private Long studentUserId;
    private Long classId;
    private Long termId;

    @MaskedField(maskType = MaskedField.MaskType.STUDENT_ID)
    @JsonSerialize(using = MaskedFieldSerializer.class)
    private String studentIdNumber;

    @MaskedField(maskType = MaskedField.MaskType.CONTACT)
    @JsonSerialize(using = MaskedFieldSerializer.class)
    private String guardianContact;

    @MaskedField(maskType = MaskedField.MaskType.NOTES)
    @JsonSerialize(using = MaskedFieldSerializer.class)
    private String accommodationNotes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RosterResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    public void setStudentUserId(Long studentUserId) {
        this.studentUserId = studentUserId;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public Long getTermId() {
        return termId;
    }

    public void setTermId(Long termId) {
        this.termId = termId;
    }

    public String getStudentIdNumber() {
        return studentIdNumber;
    }

    public void setStudentIdNumber(String studentIdNumber) {
        this.studentIdNumber = studentIdNumber;
    }

    public String getGuardianContact() {
        return guardianContact;
    }

    public void setGuardianContact(String guardianContact) {
        this.guardianContact = guardianContact;
    }

    public String getAccommodationNotes() {
        return accommodationNotes;
    }

    public void setAccommodationNotes(String accommodationNotes) {
        this.accommodationNotes = accommodationNotes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
