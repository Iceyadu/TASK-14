package com.eaglepoint.exam.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for user authentication.
 */
public class LoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Device fingerprint is required")
    private String deviceFingerprint;

    /**
     * When true, extend session up to {@code remember-device-days} on managed devices only.
     */
    private Boolean rememberDevice;

    public LoginRequest() {
    }

    public LoginRequest(String username, String password, String deviceFingerprint) {
        this.username = username;
        this.password = password;
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public Boolean getRememberDevice() {
        return rememberDevice;
    }

    public void setRememberDevice(Boolean rememberDevice) {
        this.rememberDevice = rememberDevice;
    }
}
