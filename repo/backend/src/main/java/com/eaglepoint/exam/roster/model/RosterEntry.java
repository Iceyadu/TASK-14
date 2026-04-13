package com.eaglepoint.exam.roster.model;

import com.eaglepoint.exam.security.crypto.EncryptedFieldConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Represents a student's enrollment in a class for a given term.
 * Sensitive fields (student ID number, guardian contact, accommodation notes)
 * are encrypted at rest using AES-256-GCM.
 */
@Entity
@Table(name = "roster_entries")
public class RosterEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "term_id", nullable = false)
    private Long termId;

    @Convert(converter = EncryptedFieldConverter.class)
    @Column(name = "student_id_number_enc", columnDefinition = "VARBINARY(512)")
    private String studentIdNumberEnc;

    @Convert(converter = EncryptedFieldConverter.class)
    @Column(name = "guardian_contact_enc", columnDefinition = "VARBINARY(512)")
    private String guardianContactEnc;

    @Convert(converter = EncryptedFieldConverter.class)
    @Column(name = "accommodation_notes_enc", columnDefinition = "VARBINARY(512)")
    private String accommodationNotesEnc;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public RosterEntry() {
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

    public String getStudentIdNumberEnc() {
        return studentIdNumberEnc;
    }

    public void setStudentIdNumberEnc(String studentIdNumberEnc) {
        this.studentIdNumberEnc = studentIdNumberEnc;
    }

    public String getGuardianContactEnc() {
        return guardianContactEnc;
    }

    public void setGuardianContactEnc(String guardianContactEnc) {
        this.guardianContactEnc = guardianContactEnc;
    }

    public String getAccommodationNotesEnc() {
        return accommodationNotesEnc;
    }

    public void setAccommodationNotesEnc(String accommodationNotesEnc) {
        this.accommodationNotesEnc = accommodationNotesEnc;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
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
