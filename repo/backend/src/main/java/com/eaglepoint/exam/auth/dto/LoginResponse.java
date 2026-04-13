package com.eaglepoint.exam.auth.dto;

import com.eaglepoint.exam.shared.enums.Permission;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response payload returned upon successful authentication.
 */
public class LoginResponse {

    private String sessionToken;
    private String signingKey;
    private LocalDateTime expiresAt;
    private UserInfo user;

    public LoginResponse() {
    }

    public LoginResponse(String sessionToken, String signingKey, LocalDateTime expiresAt, UserInfo user) {
        this.sessionToken = sessionToken;
        this.signingKey = signingKey;
        this.expiresAt = expiresAt;
        this.user = user;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getSigningKey() {
        return signingKey;
    }

    public void setSigningKey(String signingKey) {
        this.signingKey = signingKey;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public UserInfo getUser() {
        return user;
    }

    public void setUser(UserInfo user) {
        this.user = user;
    }

    /**
     * Lightweight user info included in the login response.
     */
    public static class UserInfo {

        private Long id;
        private String username;
        private String role;
        private List<Permission> permissions;

        public UserInfo() {
        }

        public UserInfo(Long id, String username, String role, List<Permission> permissions) {
            this.id = id;
            this.username = username;
            this.role = role;
            this.permissions = permissions;
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

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public List<Permission> getPermissions() {
            return permissions;
        }

        public void setPermissions(List<Permission> permissions) {
            this.permissions = permissions;
        }
    }
}
