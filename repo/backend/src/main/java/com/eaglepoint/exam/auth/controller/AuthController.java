package com.eaglepoint.exam.auth.controller;

import com.eaglepoint.exam.auth.dto.LoginRequest;
import com.eaglepoint.exam.auth.dto.LoginResponse;
import com.eaglepoint.exam.auth.dto.RegisterDeviceRequest;
import com.eaglepoint.exam.auth.model.ManagedDevice;
import com.eaglepoint.exam.auth.repository.ManagedDeviceRepository;
import com.eaglepoint.exam.auth.service.AuthService;
import com.eaglepoint.exam.security.annotation.RequirePermission;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.dto.ApiResponse;
import com.eaglepoint.exam.shared.enums.Permission;
import com.eaglepoint.exam.shared.enums.RolePermissions;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller handling authentication, session management, and device registration.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ManagedDeviceRepository managedDeviceRepository;
    private final UserRepository userRepository;

    public AuthController(AuthService authService,
                          ManagedDeviceRepository managedDeviceRepository,
                          UserRepository userRepository) {
        this.authService = authService;
        this.managedDeviceRepository = managedDeviceRepository;
        this.userRepository = userRepository;
    }

    /**
     * Authenticates a user and returns a session token.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Terminates the current session.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        authService.logout();
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Returns the current session context information.
     */
    @GetMapping("/session")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSession() {
        User user = userRepository.findById(RequestContext.getUserId())
                .orElseThrow(() -> new com.eaglepoint.exam.shared.exception.EntityNotFoundException(
                        "User", RequestContext.getUserId()));
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                new ArrayList<>(RolePermissions.getPermissions(user.getRole())));
        Map<String, Object> payload = new HashMap<>();
        payload.put("user", userInfo);
        payload.put("sessionId", RequestContext.getSessionId());
        return ResponseEntity.ok(ApiResponse.success(payload));
    }

    /**
     * Registers a new managed device.
     */
    @PostMapping("/devices")
    @RequirePermission(Permission.DEVICE_MANAGE)
    public ResponseEntity<ApiResponse<ManagedDevice>> registerDevice(@Valid @RequestBody RegisterDeviceRequest request) {
        ManagedDevice device = new ManagedDevice(
                request.getDeviceFingerprint(),
                request.getDescription(),
                RequestContext.getUserId()
        );
        ManagedDevice saved = managedDeviceRepository.save(device);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    /**
     * Lists all registered managed devices.
     */
    @GetMapping("/devices")
    @RequirePermission(Permission.DEVICE_MANAGE)
    public ResponseEntity<ApiResponse<List<ManagedDevice>>> listDevices() {
        List<ManagedDevice> devices = managedDeviceRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(devices));
    }

    /**
     * Removes a managed device by ID.
     */
    @DeleteMapping("/devices/{id}")
    @RequirePermission(Permission.DEVICE_MANAGE)
    public ResponseEntity<ApiResponse<Void>> removeDevice(@PathVariable Long id) {
        managedDeviceRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Terminates all sessions for a specific user. Admin-only.
     */
    @PostMapping("/sessions/{userId}/terminate")
    @RequirePermission(Permission.USER_MANAGE)
    public ResponseEntity<ApiResponse<Void>> terminateSession(@PathVariable Long userId) {
        authService.terminateSession(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Unlocks a locked user account. Admin-only.
     */
    @PostMapping("/users/{userId}/unlock")
    @RequirePermission(Permission.USER_MANAGE)
    public ResponseEntity<ApiResponse<Void>> unlockAccount(@PathVariable Long userId) {
        authService.unlockAccount(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
