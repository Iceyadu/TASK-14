package com.eaglepoint.exam.users.dto;

import com.eaglepoint.exam.shared.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload for creating a new user.
 */
public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 12, message = "Password must be at least 12 characters long")
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotNull(message = "Role is required")
    private Role role;

    private List<ScopeAssignmentDto> scopeAssignments;

    public CreateUserRequest() {
    }

    public CreateUserRequest(String username, String password, String fullName,
                             Role role, List<ScopeAssignmentDto> scopeAssignments) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.scopeAssignments = scopeAssignments;
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

    public List<ScopeAssignmentDto> getScopeAssignments() {
        return scopeAssignments;
    }

    public void setScopeAssignments(List<ScopeAssignmentDto> scopeAssignments) {
        this.scopeAssignments = scopeAssignments;
    }
}
