package com.eaglepoint.exam.users.dto;

import com.eaglepoint.exam.shared.enums.ScopeType;

/**
 * DTO representing a scope assignment for a user.
 */
public class ScopeAssignmentDto {

    private ScopeType scopeType;
    private Long scopeId;

    public ScopeAssignmentDto() {
    }

    public ScopeAssignmentDto(ScopeType scopeType, Long scopeId) {
        this.scopeType = scopeType;
        this.scopeId = scopeId;
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(ScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public Long getScopeId() {
        return scopeId;
    }

    public void setScopeId(Long scopeId) {
        this.scopeId = scopeId;
    }
}
