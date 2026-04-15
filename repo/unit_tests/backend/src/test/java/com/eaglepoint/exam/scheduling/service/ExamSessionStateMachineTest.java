package com.eaglepoint.exam.scheduling.service;

import com.eaglepoint.exam.scheduling.model.ExamSessionStatus;
import com.eaglepoint.exam.shared.exception.StateTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamSessionStateMachineTest {

    private final ExamSessionStateMachine stateMachine = new ExamSessionStateMachine();

    // ---- Valid transitions ----

    @Test
    void testValidTransitionDraftToSubmitted() {
        assertDoesNotThrow(() -> stateMachine.validateTransition(
                ExamSessionStatus.DRAFT,
                ExamSessionStatus.SUBMITTED_FOR_COMPLIANCE_REVIEW));
    }

    @ParameterizedTest
    @CsvSource({
        "SUBMITTED_FOR_COMPLIANCE_REVIEW, APPROVED",
        "SUBMITTED_FOR_COMPLIANCE_REVIEW, REJECTED",
        "APPROVED, PUBLISHED",
        "REJECTED, DRAFT",
        "PUBLISHED, UNPUBLISHED",
        "UNPUBLISHED, ARCHIVED",
        "UNPUBLISHED, PUBLISHED",
        "ARCHIVED, RESTORED",
        "RESTORED, DRAFT"
    })
    void testAllValidTransitionsAreAccepted(String from, String to) {
        assertDoesNotThrow(() -> stateMachine.validateTransition(
                ExamSessionStatus.valueOf(from),
                ExamSessionStatus.valueOf(to)));
    }

    // ---- Invalid transitions ----

    @Test
    void testInvalidTransitionDraftToPublished() {
        assertThrows(StateTransitionException.class, () -> stateMachine.validateTransition(
                ExamSessionStatus.DRAFT,
                ExamSessionStatus.PUBLISHED));
    }

    @ParameterizedTest
    @CsvSource({
        "DRAFT, APPROVED",
        "DRAFT, ARCHIVED",
        "APPROVED, DRAFT",
        "APPROVED, UNPUBLISHED",
        "PUBLISHED, ARCHIVED",
        "PUBLISHED, DRAFT",
        "ARCHIVED, PUBLISHED",
        "ARCHIVED, DRAFT"
    })
    void testInvalidTransitionsAreRejected(String from, String to) {
        assertThrows(StateTransitionException.class, () -> stateMachine.validateTransition(
                ExamSessionStatus.valueOf(from),
                ExamSessionStatus.valueOf(to)));
    }

    // ---- getValidTransitions ----

    @Test
    void testGetValidTransitionsForUnpublished() {
        Set<ExamSessionStatus> transitions = stateMachine.getValidTransitions(ExamSessionStatus.UNPUBLISHED);
        assertTrue(transitions.contains(ExamSessionStatus.ARCHIVED));
        assertTrue(transitions.contains(ExamSessionStatus.PUBLISHED));
        assertEquals(2, transitions.size());
    }

    @Test
    void testGetValidTransitionsForDraft() {
        Set<ExamSessionStatus> transitions = stateMachine.getValidTransitions(ExamSessionStatus.DRAFT);
        assertEquals(Set.of(ExamSessionStatus.SUBMITTED_FOR_COMPLIANCE_REVIEW), transitions);
    }

    @Test
    void testGetValidTransitionsForSubmitted() {
        Set<ExamSessionStatus> transitions = stateMachine.getValidTransitions(
                ExamSessionStatus.SUBMITTED_FOR_COMPLIANCE_REVIEW);
        assertTrue(transitions.contains(ExamSessionStatus.APPROVED));
        assertTrue(transitions.contains(ExamSessionStatus.REJECTED));
    }

    @Test
    void testGetValidTransitionsForPublished() {
        Set<ExamSessionStatus> transitions = stateMachine.getValidTransitions(ExamSessionStatus.PUBLISHED);
        assertEquals(Set.of(ExamSessionStatus.UNPUBLISHED), transitions);
    }

    @Test
    void testGetValidTransitionsForArchived() {
        Set<ExamSessionStatus> transitions = stateMachine.getValidTransitions(ExamSessionStatus.ARCHIVED);
        assertEquals(Set.of(ExamSessionStatus.RESTORED), transitions);
    }

    @Test
    void testGetValidTransitionsForRestored() {
        Set<ExamSessionStatus> transitions = stateMachine.getValidTransitions(ExamSessionStatus.RESTORED);
        assertEquals(Set.of(ExamSessionStatus.DRAFT), transitions);
    }
}
