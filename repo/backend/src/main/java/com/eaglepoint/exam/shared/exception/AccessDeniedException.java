package com.eaglepoint.exam.shared.exception;

/**
 * Thrown when a user does not have the required permission or scope to perform an action.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException() {
        super("Access denied");
    }

    public AccessDeniedException(String message) {
        super(message);
    }
}
