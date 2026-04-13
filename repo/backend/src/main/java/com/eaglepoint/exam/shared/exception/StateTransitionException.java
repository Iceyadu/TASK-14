package com.eaglepoint.exam.shared.exception;

/**
 * Thrown when an entity cannot transition from its current state to the requested state.
 */
public class StateTransitionException extends RuntimeException {

    private final String fromState;
    private final String toState;

    public StateTransitionException(String fromState, String toState) {
        super("Invalid state transition from '" + fromState + "' to '" + toState + "'");
        this.fromState = fromState;
        this.toState = toState;
    }

    public StateTransitionException(String fromState, String toState, String detail) {
        super("Invalid state transition from '" + fromState + "' to '" + toState + "': " + detail);
        this.fromState = fromState;
        this.toState = toState;
    }

    public String getFromState() {
        return fromState;
    }

    public String getToState() {
        return toState;
    }
}
