package com.eaglepoint.exam.users.controller;

import com.eaglepoint.exam.security.annotation.RequirePermission;
import com.eaglepoint.exam.shared.dto.ApiResponse;
import com.eaglepoint.exam.shared.dto.PaginationInfo;
import com.eaglepoint.exam.shared.enums.Permission;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.users.dto.CreateUserRequest;
import com.eaglepoint.exam.users.dto.ScopeAssignmentDto;
import com.eaglepoint.exam.users.dto.UpdateUserRequest;
import com.eaglepoint.exam.users.dto.UserResponse;
import com.eaglepoint.exam.users.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for user management operations. All endpoints require USER_MANAGE permission.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Lists users with optional role filter and search, paginated.
     */
    @GetMapping
    @RequirePermission(Permission.USER_MANAGE)
    public ResponseEntity<ApiResponse<List<UserResponse>>> listUsers(
            Pageable pageable,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String search) {

        Page<UserResponse> page = userService.listUsers(pageable, role, search);

        PaginationInfo pagination = new PaginationInfo(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.success(page.getContent(), pagination));
    }

    /**
     * Creates a new user.
     */
    @PostMapping
    @RequirePermission(Permission.USER_MANAGE)
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Retrieves a single user by ID.
     */
    @GetMapping("/{id}")
    @RequirePermission(Permission.USER_MANAGE)
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long id) {
        UserResponse response = userService.getUser(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Updates a user's mutable fields.
     */
    @PutMapping("/{id}")
    @RequirePermission(Permission.USER_MANAGE)
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Replaces all scope assignments for a user.
     */
    @PutMapping("/{id}/scope")
    @RequirePermission(Permission.USER_MANAGE)
    public ResponseEntity<ApiResponse<UserResponse>> updateScope(
            @PathVariable Long id,
            @Valid @RequestBody List<ScopeAssignmentDto> scopeAssignments) {
        UserResponse response = userService.updateScope(id, scopeAssignments);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Toggles concurrent sessions for a user.
     */
    @PutMapping("/{id}/concurrent-sessions")
    @RequirePermission(Permission.USER_MANAGE)
    public ResponseEntity<ApiResponse<UserResponse>> toggleConcurrentSessions(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        boolean allowed = body.getOrDefault("allowed", false);
        UserResponse response = userService.toggleConcurrentSessions(id, allowed);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
