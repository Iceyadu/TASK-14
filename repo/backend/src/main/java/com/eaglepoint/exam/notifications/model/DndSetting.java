package com.eaglepoint.exam.notifications.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalTime;

/**
 * Do Not Disturb window configuration for a student.
 * Notifications arriving during this window are held for in-app delivery only.
 */
@Entity
@Table(name = "dnd_settings")
public class DndSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_user_id", nullable = false, unique = true)
    private Long studentUserId;

    @Column(name = "dnd_start")
    private LocalTime dndStart;

    @Column(name = "dnd_end")
    private LocalTime dndEnd;

    public DndSetting() {
    }

    public DndSetting(Long studentUserId, LocalTime dndStart, LocalTime dndEnd) {
        this.studentUserId = studentUserId;
        this.dndStart = dndStart;
        this.dndEnd = dndEnd;
    }

    // ---- Getters / Setters ----

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

    public LocalTime getDndStart() {
        return dndStart;
    }

    public void setDndStart(LocalTime dndStart) {
        this.dndStart = dndStart;
    }

    public LocalTime getDndEnd() {
        return dndEnd;
    }

    public void setDndEnd(LocalTime dndEnd) {
        this.dndEnd = dndEnd;
    }
}
