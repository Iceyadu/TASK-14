package com.eaglepoint.exam.users.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.auth.model.PasswordHistory;
import com.eaglepoint.exam.auth.repository.PasswordHistoryRepository;
import com.eaglepoint.exam.auth.service.AuthService;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.model.UserScopeAssignment;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.security.repository.UserScopeAssignmentRepository;
import com.eaglepoint.exam.shared.dto.FieldError;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import com.eaglepoint.exam.users.dto.CreateUserRequest;
import com.eaglepoint.exam.users.dto.ScopeAssignmentDto;
import com.eaglepoint.exam.users.dto.UpdateUserRequest;
import com.eaglepoint.exam.users.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for user CRUD operations, scope management, and related admin functions.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserScopeAssignmentRepository scopeAssignmentRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final AuthService authService;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       UserScopeAssignmentRepository scopeAssignmentRepository,
                       PasswordHistoryRepository passwordHistoryRepository,
                       AuthService authService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.scopeAssignmentRepository = scopeAssignmentRepository;
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.authService = authService;
        this.auditService = auditService;
    }

    /**
     * Returns a paginated list of users with optional role filter and search.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable, Role roleFilter, String search) {
        Page<User> userPage;

        if (roleFilter != null && search != null && !search.isBlank()) {
            // Both filters active
            userPage = userRepository.findAll(pageable);
            // Post-filter since the repository doesn't have a combined query
            // For production, a custom @Query would be better
            List<User> filtered = userPage.getContent().stream()
                    .filter(u -> u.getRole() == roleFilter)
                    .filter(u -> u.getUsername().toLowerCase().contains(search.toLowerCase())
                            || u.getFullName().toLowerCase().contains(search.toLowerCase()))
                    .toList();
            return new org.springframework.data.domain.PageImpl<>(
                    filtered.stream().map(this::toUserResponse).toList(),
                    pageable,
                    filtered.size()
            );
        } else if (roleFilter != null) {
            userPage = userRepository.findAll(pageable);
            List<User> filtered = userPage.getContent().stream()
                    .filter(u -> u.getRole() == roleFilter)
                    .toList();
            return new org.springframework.data.domain.PageImpl<>(
                    filtered.stream().map(this::toUserResponse).toList(),
                    pageable,
                    filtered.size()
            );
        } else if (search != null && !search.isBlank()) {
            userPage = userRepository.findAll(pageable);
            List<User> filtered = userPage.getContent().stream()
                    .filter(u -> u.getUsername().toLowerCase().contains(search.toLowerCase())
                            || u.getFullName().toLowerCase().contains(search.toLowerCase()))
                    .toList();
            return new org.springframework.data.domain.PageImpl<>(
                    filtered.stream().map(this::toUserResponse).toList(),
                    pageable,
                    filtered.size()
            );
        } else {
            userPage = userRepository.findAll(pageable);
        }

        return userPage.map(this::toUserResponse);
    }

    /**
     * Retrieves a single user by ID.
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User", id));
        return toUserResponse(user);
    }

    /**
     * Creates a new user with password validation, hashing, scope assignments, and audit logging.
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // Validate password complexity
        List<FieldError> passwordErrors = authService.validatePasswordComplexity(request.getPassword());
        if (!passwordErrors.isEmpty()) {
            throw new IllegalArgumentException("Password does not meet complexity requirements: "
                    + passwordErrors.stream().map(FieldError::message).collect(Collectors.joining("; ")));
        }

        // Hash password
        String hashedPassword = authService.getPasswordEncoder().encode(request.getPassword());

        // Create user entity
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(hashedPassword);
        user.setFullName(request.getFullName());
        user.setRole(request.getRole());
        user.setAllowConcurrentSessions(false);
        user.setFailedLoginAttempts(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);

        // Save scope assignments
        if (request.getScopeAssignments() != null && !request.getScopeAssignments().isEmpty()) {
            List<UserScopeAssignment> assignments = request.getScopeAssignments().stream()
                    .map(dto -> new UserScopeAssignment(savedUser.getId(), dto.getScopeType(), dto.getScopeId()))
                    .toList();
            scopeAssignmentRepository.saveAll(assignments);
        }

        // Save to password history
        PasswordHistory history = new PasswordHistory(savedUser.getId(), hashedPassword);
        passwordHistoryRepository.save(history);

        // Audit
        auditService.logAction("CREATE_USER", "User", savedUser.getId(),
                null, "username=" + savedUser.getUsername() + ", role=" + savedUser.getRole(),
                "User created");

        return toUserResponse(savedUser);
    }

    /**
     * Updates an existing user's mutable fields.
     */
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User", id));

        String oldState = "fullName=" + user.getFullName()
                + ", role=" + user.getRole()
                + ", allowConcurrentSessions=" + user.isAllowConcurrentSessions();

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getAllowConcurrentSessions() != null) {
            user.setAllowConcurrentSessions(request.getAllowConcurrentSessions());
        }

        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        String newState = "fullName=" + saved.getFullName()
                + ", role=" + saved.getRole()
                + ", allowConcurrentSessions=" + saved.isAllowConcurrentSessions();

        auditService.logAction("UPDATE_USER", "User", id,
                oldState, newState, "User updated");

        return toUserResponse(saved);
    }

    /**
     * Replaces all scope assignments for a user.
     */
    @Transactional
    public UserResponse updateScope(Long id, List<ScopeAssignmentDto> scopeAssignments) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User", id));

        // Delete old assignments
        List<UserScopeAssignment> oldAssignments = scopeAssignmentRepository.findByUserId(id);
        String oldState = oldAssignments.stream()
                .map(a -> a.getScopeType() + ":" + a.getScopeId())
                .collect(Collectors.joining(", "));
        scopeAssignmentRepository.deleteAll(oldAssignments);

        // Save new assignments
        if (scopeAssignments != null && !scopeAssignments.isEmpty()) {
            List<UserScopeAssignment> newAssignments = scopeAssignments.stream()
                    .map(dto -> new UserScopeAssignment(id, dto.getScopeType(), dto.getScopeId()))
                    .toList();
            scopeAssignmentRepository.saveAll(newAssignments);
        }

        String newState = scopeAssignments == null ? "" : scopeAssignments.stream()
                .map(a -> a.getScopeType() + ":" + a.getScopeId())
                .collect(Collectors.joining(", "));

        auditService.logAction("UPDATE_SCOPE", "User", id,
                oldState, newState, "Scope assignments updated");

        return toUserResponse(user);
    }

    /**
     * Toggles the concurrent sessions flag for a user.
     */
    @Transactional
    public UserResponse toggleConcurrentSessions(Long id, boolean allowed) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User", id));

        boolean oldValue = user.isAllowConcurrentSessions();
        user.setAllowConcurrentSessions(allowed);
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        auditService.logAction("TOGGLE_CONCURRENT_SESSIONS", "User", id,
                "allowConcurrentSessions=" + oldValue,
                "allowConcurrentSessions=" + allowed,
                "Concurrent sessions " + (allowed ? "enabled" : "disabled"));

        return toUserResponse(saved);
    }

    /**
     * Maps a User entity to a UserResponse DTO, including scope assignments.
     */
    private UserResponse toUserResponse(User user) {
        List<UserScopeAssignment> assignments = scopeAssignmentRepository.findByUserId(user.getId());
        List<ScopeAssignmentDto> scopeDtos = assignments.stream()
                .map(a -> new ScopeAssignmentDto(a.getScopeType(), a.getScopeId()))
                .toList();

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole(),
                user.isAllowConcurrentSessions(),
                scopeDtos,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
