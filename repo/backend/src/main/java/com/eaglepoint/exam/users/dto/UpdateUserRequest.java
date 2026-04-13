package com.eaglepoint.exam.users.dto;

import com.eaglepoint.exam.shared.enums.Role;

/**
 * Request payload for updating an existing user.
 * All fields are optional; only non-null fields will be applied.
 */
public class UpdateUserRequest {

    private String fullName;
    private Role role;
    private Boolean allowConcurrentSessions;

    public UpdateUserRequest() {
    }

    public UpdateUserRequest(String fullName, Role role, Boolean allowConcurrentSessions) {
        this.fullName = fullName;
        this.role = role;
        this.allowConcurrentSessions = allowConcurrentSessions;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Boolean getAllowConcurrentSessions() {
        return allowConcurrentSessions;
    }

    public void setAllowConcurrentSessions(Boolean allowConcurrentSessions) {
        this.allowConcurrentSessions = allowConcurrentSessions;
    }
}
