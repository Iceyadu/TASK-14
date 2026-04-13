package com.eaglepoint.exam.scheduling.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link ExamSessionClass}.
 */
public class ExamSessionClassId implements Serializable {

    private Long examSessionId;
    private Long classId;

    public ExamSessionClassId() {
    }

    public ExamSessionClassId(Long examSessionId, Long classId) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExamSessionClassId that = (ExamSessionClassId) o;
        return Objects.equals(examSessionId, that.examSessionId)
                && Objects.equals(classId, that.classId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(examSessionId, classId);
    }
}
