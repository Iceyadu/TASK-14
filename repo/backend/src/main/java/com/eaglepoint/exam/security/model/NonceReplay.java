package com.eaglepoint.exam.security.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Tracks recently used nonces to prevent replay attacks.
 * Nonces expire after a short window and can be purged periodically.
 */
@Entity
@Table(name = "nonce_replay")
public class NonceReplay {

    @Id
    @Column(name = "nonce", nullable = false, length = 36)
    private String nonce;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    protected NonceReplay() {
    }

    public NonceReplay(String nonce, LocalDateTime expiresAt) {
        this.nonce = nonce;
        this.expiresAt = expiresAt;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
