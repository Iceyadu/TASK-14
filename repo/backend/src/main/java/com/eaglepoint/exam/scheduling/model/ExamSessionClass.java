package com.eaglepoint.exam.scheduling.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Join entity linking an exam session to a class.
 */
@Entity
@Table(name = "exam_session_classes")
@IdClass(ExamSessionClassId.class)
public class ExamSessionClass {

    @Id
    @Column(name = "exam_session_id", nullable = false)
    private Long examSessionId;

    @Id
    @Column(name = "class_id", nullable = false)
    private Long classId;

    public ExamSessionClass() {
    }

    public ExamSessionClass(Long examSessionId, Long classId) {
        this.examSessionId = examSessionId;
        this.classId = classId;
    }

    public Long getExamSessionId() {
        return examSessionId;
    }

    public void setExamSessionId(Long examSessionId) {
        this.examSessionId = examSessionId;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }
}
