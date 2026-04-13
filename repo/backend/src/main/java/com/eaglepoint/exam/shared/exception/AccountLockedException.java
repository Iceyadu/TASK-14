package com.eaglepoint.exam.shared.exception;

import java.time.LocalDateTime;

/**
 * Thrown when a user account is temporarily locked due to repeated failed login attempts.
 */
public class AccountLockedException extends RuntimeException {

    private final LocalDateTime unlockAt;

    public AccountLockedException(LocalDateTime unlockAt) {
        super("Account is locked until " + unlockAt);
        this.unlockAt = unlockAt;
    }

    public AccountLockedException(String message, LocalDateTime unlockAt) {
        super(message);
        this.unlockAt = unlockAt;
    }

    public LocalDateTime getUnlockAt() {
        return unlockAt;
    }
}
