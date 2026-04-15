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
import com.eaglepoint.exam.shared.enums.ScopeType;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import com.eaglepoint.exam.users.dto.CreateUserRequest;
import com.eaglepoint.exam.users.dto.ScopeAssignmentDto;
import com.eaglepoint.exam.users.dto.UpdateUserRequest;
import com.eaglepoint.exam.users.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserScopeAssignmentRepository scopeAssignmentRepository;
    @Mock private PasswordHistoryRepository passwordHistoryRepository;
    @Mock private AuthService authService;
    @Mock private AuditService auditService;

    private UserService newService() {
        return new UserService(userRepository, scopeAssignmentRepository,
                passwordHistoryRepository, authService, auditService);
    }

    private User sampleUser(Long id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("hashed");
        user.setFullName("Full " + username);
        user.setRole(role);
        user.setAllowConcurrentSessions(false);
        user.setFailedLoginAttempts(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    // ---- createUser ----

    @Test
    void testCreateUserRejectsWeakPassword() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("u1");
        request.setPassword("weak");
        request.setFullName("User One");
        request.setRole(Role.STUDENT);

        when(authService.validatePasswordComplexity("weak"))
                .thenReturn(List.of(new FieldError("password", "too weak")));

        assertThrows(IllegalArgumentException.class, () -> newService().createUser(request));
    }

    @Test
    void testCreateUserPersistsPasswordHistoryAndAudit() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("u2");
        request.setPassword("Strong@123456");
        request.setFullName("User Two");
        request.setRole(Role.STUDENT);

        when(authService.validatePasswordComplexity("Strong@123456")).thenReturn(List.of());
        when(authService.getPasswordEncoder()).thenReturn(new BCryptPasswordEncoder(4));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(77L);
            return u;
        });
        when(scopeAssignmentRepository.findByUserId(77L)).thenReturn(List.of());

        newService().createUser(request);

        ArgumentCaptor<PasswordHistory> historyCaptor = ArgumentCaptor.forClass(PasswordHistory.class);
        verify(passwordHistoryRepository).save(historyCaptor.capture());
        assertEquals(77L, historyCaptor.getValue().getUserId());
        verify(auditService).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testCreateUserWithScopeAssignmentsSavesAssignments() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("u3");
        request.setPassword("Strong@123456");
        request.setFullName("User Three");
        request.setRole(Role.HOMEROOM_TEACHER);
        request.setScopeAssignments(List.of(new ScopeAssignmentDto(ScopeType.CLASS, 50L)));

        when(authService.validatePasswordComplexity("Strong@123456")).thenReturn(List.of());
        when(authService.getPasswordEncoder()).thenReturn(new BCryptPasswordEncoder(4));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(88L);
            return u;
        });
        when(scopeAssignmentRepository.findByUserId(88L)).thenReturn(List.of());

        newService().createUser(request);

        ArgumentCaptor<List> scopeCaptor = ArgumentCaptor.forClass(List.class);
        verify(scopeAssignmentRepository).saveAll(scopeCaptor.capture());
        assertEquals(1, scopeCaptor.getValue().size());
    }

    // ---- getUser ----

    @Test
    void testGetUserThrowsForMissingId() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> newService().getUser(999L));
    }

    @Test
    void testGetUserReturnsCorrectResponse() {
        User user = sampleUser(10L, "alice", Role.ADMIN);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(scopeAssignmentRepository.findByUserId(10L)).thenReturn(List.of());

        UserResponse response = newService().getUser(10L);
        assertEquals("alice", response.getUsername());
        assertEquals(Role.ADMIN, response.getRole());
    }

    // ---- updateUser ----

    @Test
    void testUpdateUserChangesFullNameAndAudits() {
        User user = sampleUser(20L, "bob", Role.STUDENT);
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(scopeAssignmentRepository.findByUserId(20L)).thenReturn(List.of());

        UpdateUserRequest req = new UpdateUserRequest("Bob Updated", null, null);
        UserResponse response = newService().updateUser(20L, req);

        assertEquals("Bob Updated", response.getFullName());
        verify(auditService).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testUpdateUserChangesRole() {
        User user = sampleUser(21L, "carol", Role.STUDENT);
        when(userRepository.findById(21L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(scopeAssignmentRepository.findByUserId(21L)).thenReturn(List.of());

        UpdateUserRequest req = new UpdateUserRequest(null, Role.HOMEROOM_TEACHER, null);
        UserResponse response = newService().updateUser(21L, req);

        assertEquals(Role.HOMEROOM_TEACHER, response.getRole());
    }

    @Test
    void testUpdateUserThrowsForMissingId() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> newService().updateUser(999L, new UpdateUserRequest()));
    }

    // ---- updateScope ----

    @Test
    void testUpdateScopeReplacesOldAssignments() {
        User user = sampleUser(30L, "dave", Role.HOMEROOM_TEACHER);
        when(userRepository.findById(30L)).thenReturn(Optional.of(user));
        when(scopeAssignmentRepository.findByUserId(30L)).thenReturn(
                List.of(new UserScopeAssignment(30L, ScopeType.CLASS, 5L)));

        List<ScopeAssignmentDto> newScopes = List.of(
                new ScopeAssignmentDto(ScopeType.CLASS, 10L),
                new ScopeAssignmentDto(ScopeType.CAMPUS, 2L));

        newService().updateScope(30L, newScopes);

        verify(scopeAssignmentRepository).deleteAll(any());
        verify(scopeAssignmentRepository).saveAll(any());
        verify(auditService).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testUpdateScopeWithEmptyListClearsAssignments() {
        User user = sampleUser(31L, "eve", Role.ACADEMIC_COORDINATOR);
        when(userRepository.findById(31L)).thenReturn(Optional.of(user));
        when(scopeAssignmentRepository.findByUserId(31L)).thenReturn(
                List.of(new UserScopeAssignment(31L, ScopeType.TERM, 3L)));

        newService().updateScope(31L, List.of());

        verify(scopeAssignmentRepository).deleteAll(any());
    }

    // ---- toggleConcurrentSessions ----

    @Test
    void testToggleConcurrentSessionsEnablesAndAudits() {
        User user = sampleUser(40L, "frank", Role.STUDENT);
        user.setAllowConcurrentSessions(false);
        when(userRepository.findById(40L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(scopeAssignmentRepository.findByUserId(40L)).thenReturn(List.of());

        UserResponse response = newService().toggleConcurrentSessions(40L, true);

        assertTrue(response.isAllowConcurrentSessions());
        verify(auditService).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testToggleConcurrentSessionsDisables() {
        User user = sampleUser(41L, "grace", Role.STUDENT);
        user.setAllowConcurrentSessions(true);
        when(userRepository.findById(41L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(scopeAssignmentRepository.findByUserId(41L)).thenReturn(List.of());

        UserResponse response = newService().toggleConcurrentSessions(41L, false);

        assertFalse(response.isAllowConcurrentSessions());
    }
}
