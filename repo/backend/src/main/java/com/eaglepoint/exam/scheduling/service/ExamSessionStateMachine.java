package com.eaglepoint.exam.scheduling.service;

import com.eaglepoint.exam.scheduling.model.ExamSessionStatus;
import com.eaglepoint.exam.shared.exception.StateTransitionException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Defines and enforces the legal state transitions for exam sessions.
 */
@Component
public class ExamSessionStateMachine {

    private static final Map<ExamSessionStatus, Set<ExamSessionStatus>> VALID_TRANSITIONS;

    static {
        Map<ExamSessionStatus, Set<ExamSessionStatus>> map = new EnumMap<>(ExamSessionStatus.class);

        map.put(ExamSessionStatus.DRAFT, Collections.unmodifiableSet(
                EnumSet.of(ExamSessionStatus.SUBMITTED_FOR_COMPLIANCE_REVIEW)));

        map.put(ExamSessionStatus.SUBMITTED_FOR_COMPLIANCE_REVIEW, Collections.unmodifiableSet(
                EnumSet.of(ExamSessionStatus.APPROVED, ExamSessionStatus.REJECTED)));

        map.put(ExamSessionStatus.APPROVED, Collections.unmodifiableSet(
                EnumSet.of(ExamSessionStatus.PUBLISHED)));

        map.put(ExamSessionStatus.REJECTED, Collections.unmodifiableSet(
                EnumSet.of(ExamSessionStatus.DRAFT)));

        map.put(ExamSessionStatus.PUBLISHED, Collections.unmodifiableSet(
                EnumSet.of(ExamSessionStatus.UNPUBLISHED)));

        map.put(ExamSessionStatus.UNPUBLISHED, Collections.unmodifiableSet(
                EnumSet.of(ExamSessionStatus.ARCHIVED, ExamSessionStatus.PUBLISHED)));

        map.put(ExamSessionStatus.ARCHIVED, Collections.unmodifiableSet(
                EnumSet.of(ExamSessionStatus.RESTORED)));

        map.put(ExamSessionStatus.RESTORED, Collections.unmodifiableSet(
                EnumSet.of(ExamSessionStatus.DRAFT)));

        VALID_TRANSITIONS = Collections.unmodifiableMap(map);
    }

    /**
     * Validates that the transition from {@code from} to {@code to} is legal.
     *
     * @throws StateTransitionException if the transition is not allowed
     */
    public void validateTransition(ExamSessionStatus from, ExamSessionStatus to) {
        Set<ExamSessionStatus> allowed = VALID_TRANSITIONS.getOrDefault(from, Collections.emptySet());
        if (!allowed.contains(to)) {
            throw new StateTransitionException(from.name(), to.name());
        }
    }

    /**
     * Returns the set of states that are reachable from the given current state.
     */
    public Set<ExamSessionStatus> getValidTransitions(ExamSessionStatus current) {
        return VALID_TRANSITIONS.getOrDefault(current, Collections.emptySet());
    }
}
