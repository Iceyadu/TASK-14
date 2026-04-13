package com.eaglepoint.exam.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Represents a trusted device registered by an administrator.
 * Sessions originating from managed devices may receive extended expiry times.
 */
@Entity
@Table(name = "managed_devices")
public class ManagedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_fingerprint", nullable = false, unique = true)
    private String deviceFingerprint;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "registered_by", nullable = false)
    private Long registeredBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ManagedDevice() {
    }

    public ManagedDevice(String deviceFingerprint, String description, Long registeredBy) {
        this.deviceFingerprint = deviceFingerprint;
        this.description = description;
        this.registeredBy = registeredBy;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getRegisteredBy() {
        return registeredBy;
    }

    public void setRegisteredBy(Long registeredBy) {
        this.registeredBy = registeredBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
