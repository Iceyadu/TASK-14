package com.eaglepoint.exam.security.repository;

import com.eaglepoint.exam.security.model.UserScopeAssignment;
import com.eaglepoint.exam.shared.enums.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for user scope assignment lookups.
 */
@Repository
public interface UserScopeAssignmentRepository extends JpaRepository<UserScopeAssignment, Long> {

    List<UserScopeAssignment> findByUserId(Long userId);

    List<UserScopeAssignment> findByUserIdAndScopeType(Long userId, ScopeType scopeType);

    boolean existsByUserIdAndScopeTypeAndScopeId(Long userId, ScopeType scopeType, Long scopeId);
}
