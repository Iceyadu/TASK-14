package com.eaglepoint.exam.shared.exception;

/**
 * Thrown when a client exceeds the allowed request rate.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException() {
        super("Rate limit exceeded. Please try again later.");
    }

    public RateLimitExceededException(String message) {
        super(message);
    }
}
