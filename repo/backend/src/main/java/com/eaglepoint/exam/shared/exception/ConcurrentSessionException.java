package com.eaglepoint.exam.shared.exception;

/**
 * Thrown when a user already has an active session and concurrent sessions are not allowed.
 */
public class ConcurrentSessionException extends RuntimeException {

    public ConcurrentSessionException() {
        super("An active session already exists for this user");
    }

    public ConcurrentSessionException(String message) {
        super(message);
    }
}
