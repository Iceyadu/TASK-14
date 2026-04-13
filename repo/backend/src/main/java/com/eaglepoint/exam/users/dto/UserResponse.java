package com.eaglepoint.exam.users.dto;

import com.eaglepoint.exam.shared.enums.Role;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for user data. Excludes sensitive fields such as passwordHash.
 */
public class UserResponse {

    private Long id;
    private String username;
    private String fullName;
    private Role role;
    private boolean allowConcurrentSessions;
    private List<ScopeAssignmentDto> scopeAssignments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public UserResponse() {
    }

    public UserResponse(Long id, String username, String fullName, Role role,
                        boolean allowConcurrentSessions, List<ScopeAssignmentDto> scopeAssignments,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.allowConcurrentSessions = allowConcurrentSessions;
        this.scopeAssignments = scopeAssignments;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public boolean isAllowConcurrentSessions() {
        return allowConcurrentSessions;
    }

    public void setAllowConcurrentSessions(boolean allowConcurrentSessions) {
        this.allowConcurrentSessions = allowConcurrentSessions;
    }

    public List<ScopeAssignmentDto> getScopeAssignments() {
        return scopeAssignments;
    }

    public void setScopeAssignments(List<ScopeAssignmentDto> scopeAssignments) {
        this.scopeAssignments = scopeAssignments;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
