package com.eaglepoint.exam.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for registering a managed device.
 */
public class RegisterDeviceRequest {

    @NotBlank(message = "Device fingerprint is required")
    private String deviceFingerprint;

    @NotBlank(message = "Description is required")
    private String description;

    public RegisterDeviceRequest() {
    }

    public RegisterDeviceRequest(String deviceFingerprint, String description) {
        this.deviceFingerprint = deviceFingerprint;
        this.description = description;
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
}
