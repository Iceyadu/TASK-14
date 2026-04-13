package com.eaglepoint.exam.roster.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for creating or updating a roster entry.
 */
public class CreateRosterRequest {

    @NotNull(message = "Student user ID is required")
    private Long studentUserId;

    @NotNull(message = "Class ID is required")
    private Long classId;

    @NotNull(message = "Term ID is required")
    private Long termId;

    private String studentIdNumber;

    private String guardianContact;

    private String accommodationNotes;

    public CreateRosterRequest() {
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
}
