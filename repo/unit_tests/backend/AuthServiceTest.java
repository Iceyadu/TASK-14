package com.eaglepoint.exam.auth.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.auth.dto.LoginRequest;
import com.eaglepoint.exam.auth.dto.LoginResponse;
import com.eaglepoint.exam.auth.repository.ManagedDeviceRepository;
import com.eaglepoint.exam.auth.repository.PasswordHistoryRepository;
import com.eaglepoint.exam.security.model.Session;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.repository.SessionRepository;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.dto.FieldError;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.AccountLockedException;
import com.eaglepoint.exam.shared.exception.ConcurrentSessionException;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService} covering login, lockout, session management,
 * password complexity, and logout flows.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private ManagedDeviceRepository managedDeviceRepository;

    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        ReflectionTestUtils.setField(authService, "appSigningSecret", "test-secret-key-for-hmac");
        ReflectionTestUtils.setField(authService, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(authService, "lockoutMinutes", 15);
        ReflectionTestUtils.setField(authService, "shortSessionMinutes", 30);
        ReflectionTestUtils.setField(authService, "rememberDeviceDays", 7);
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    // ---- Helper factory methods ----

    private User createUser(Long id, String username, String rawPassword, Role role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setFullName("Test User");
        user.setAllowConcurrentSessions(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    private LoginRequest createLoginRequest(String username, String password, String device) {
        return new LoginRequest(username, password, device);
    }

    // ---- Tests ----

    @Test
    void testLoginSuccess() {
        User user = createUser(1L, "teacher1", "CorrectPassword1!", Role.HOMEROOM_TEACHER);
        LoginRequest request = createLoginRequest("teacher1", "CorrectPassword1!", "device-abc");

        when(userRepository.findByUsername("teacher1")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(managedDeviceRepository.existsByDeviceFingerprint("device-abc")).thenReturn(false);
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 100L);
            return s;
        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertNotNull(response.getSessionToken());
        assertNotNull(response.getSigningKey());
        assertNotNull(response.getExpiresAt());
        assertNotNull(response.getUser());
        assertEquals("teacher1", response.getUser().getUsername());
        assertEquals(0, user.getFailedLoginAttempts());

        verify(sessionRepository).save(any(Session.class));
        verify(auditService).logAction(eq("LOGIN"), eq("Session"), anyLong(), isNull(), isNull(), anyString());
    }

    @Test
    void testLoginInvalidPassword() {
        User user = createUser(1L, "teacher1", "CorrectPassword1!", Role.HOMEROOM_TEACHER);
        user.setFailedLoginAttempts(0);
        LoginRequest request = createLoginRequest("teacher1", "WrongPassword!", "device-abc");

        when(userRepository.findByUsername("teacher1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));

        assertEquals(1, user.getFailedLoginAttempts());
        verify(userRepository).save(user);
    }

    @Test
    void testAccountLockout() {
        User user = createUser(1L, "teacher1", "CorrectPassword1!", Role.HOMEROOM_TEACHER);
        user.setFailedLoginAttempts(4); // one more triggers lockout
        LoginRequest request = createLoginRequest("teacher1", "WrongPassword!", "device-abc");

        when(userRepository.findByUsername("teacher1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountLockedException ex = assertThrows(AccountLockedException.class, () -> authService.login(request));

        assertNotNull(ex.getUnlockAt());
        assertTrue(ex.getUnlockAt().isAfter(LocalDateTime.now()));
        assertEquals(5, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil());
        verify(auditService).logAction(eq("ACCOUNT_LOCKED"), eq("User"), eq(1L), isNull(), isNull(), anyString());
    }

    @Test
    void testConcurrentSessionBlocked() {
        User user = createUser(1L, "teacher1", "CorrectPassword1!", Role.HOMEROOM_TEACHER);
        user.setAllowConcurrentSessions(false);
        LoginRequest request = createLoginRequest("teacher1", "CorrectPassword1!", "device-new");

        Session existingSession = new Session();
        existingSession.setDeviceFingerprint("device-old");
        existingSession.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(userRepository.findByUsername("teacher1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.findByUserId(1L)).thenReturn(List.of(existingSession));

        assertThrows(ConcurrentSessionException.class, () -> authService.login(request));
    }

    @Test
    void testConcurrentSessionAllowedByAdmin() {
        User user = createUser(1L, "admin1", "CorrectPassword1!", Role.ADMIN);
        user.setAllowConcurrentSessions(true);
        LoginRequest request = createLoginRequest("admin1", "CorrectPassword1!", "device-new");

        Session existingSession = new Session();
        existingSession.setDeviceFingerprint("device-old");
        existingSession.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.findByUserId(1L)).thenReturn(List.of(existingSession));
        when(managedDeviceRepository.existsByDeviceFingerprint("device-new")).thenReturn(false);
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 200L);
            return s;
        });

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertNotNull(response.getSessionToken());
    }

    @Test
    void testPasswordComplexityValid() {
        List<FieldError> errors = authService.validatePasswordComplexity("Str0ngP@ssw0rd!");

        assertTrue(errors.isEmpty(), "A 12+ char password with upper, lower, digit, special should pass");
    }

    @Test
    void testPasswordComplexityInvalid() {
        List<FieldError> errors = authService.validatePasswordComplexity("short");

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.message().contains("at least 12 characters")));
    }

    @Test
    void testPasswordComplexityMissingUppercase() {
        List<FieldError> errors = authService.validatePasswordComplexity("alllowercase1!");

        assertTrue(errors.stream().anyMatch(e -> e.message().contains("uppercase")));
    }

    @Test
    void testSessionExpiry() {
        // A session created with standard (non-managed) device should have 30min expiry
        User user = createUser(1L, "teacher1", "CorrectPassword1!", Role.HOMEROOM_TEACHER);
        LoginRequest request = createLoginRequest("teacher1", "CorrectPassword1!", "device-abc");

        when(userRepository.findByUsername("teacher1")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(managedDeviceRepository.existsByDeviceFingerprint("device-abc")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 100L);
            return s;
        });

        LoginResponse response = authService.login(request);

        long minutes = java.time.Duration.between(LocalDateTime.now(), response.getExpiresAt()).toMinutes();
        assertTrue(minutes >= 29 && minutes <= 30, "Expected ~30 minute session, got " + minutes + " minutes");
    }

    @Test
    void testRememberDeviceManagedOnly() {
        // Non-managed device should get standard 30min expiry
        User user = createUser(1L, "teacher1", "CorrectPassword1!", Role.HOMEROOM_TEACHER);
        LoginRequest request = createLoginRequest("teacher1", "CorrectPassword1!", "unmanaged-device");

        when(userRepository.findByUsername("teacher1")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(managedDeviceRepository.existsByDeviceFingerprint("unmanaged-device")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        when(sessionRepository.save(sessionCaptor.capture())).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 100L);
            return s;
        });

        authService.login(request);

        Session savedSession = sessionCaptor.getAllValues().get(0);
        assertFalse(savedSession.isRememberDevice());
        // Expiry should be ~30 min, not 7 days
        assertTrue(savedSession.getExpiresAt().isBefore(LocalDateTime.now().plusHours(1)));
    }

    @Test
    void testRememberDeviceManaged() {
        // Managed device should get 7 day expiry
        User user = createUser(1L, "teacher1", "CorrectPassword1!", Role.HOMEROOM_TEACHER);
        LoginRequest request = createLoginRequest("teacher1", "CorrectPassword1!", "managed-device");
        request.setRememberDevice(true);

        when(userRepository.findByUsername("teacher1")).thenReturn(Optional.of(user));
        when(sessionRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(managedDeviceRepository.existsByDeviceFingerprint("managed-device")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        when(sessionRepository.save(sessionCaptor.capture())).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 100L);
            return s;
        });

        authService.login(request);

        Session savedSession = sessionCaptor.getAllValues().get(0);
        assertTrue(savedSession.isRememberDevice());
        // Expiry should be ~7 days from now
        assertTrue(savedSession.getExpiresAt().isAfter(LocalDateTime.now().plusDays(6)));
    }

    @Test
    void testLogout() {
        RequestContext.set(1L, "teacher1", Role.HOMEROOM_TEACHER, "session-token-123", "127.0.0.1", "trace-1");

        Session session = new Session();
        ReflectionTestUtils.setField(session, "id", 50L);
        session.setSessionToken("session-token-123");
        session.setUserId(1L);

        when(sessionRepository.findBySessionToken("session-token-123")).thenReturn(Optional.of(session));

        authService.logout();

        verify(sessionRepository).delete(session);
        verify(auditService).logAction(eq("LOGOUT"), eq("Session"), eq(50L), isNull(), isNull(), eq("User logged out"));
    }

    @Test
    void testUnlockAccount() {
        RequestContext.set(99L, "admin", Role.ADMIN, "admin-session", "127.0.0.1", "trace-2");

        User lockedUser = createUser(5L, "locked_user", "Password123!", Role.HOMEROOM_TEACHER);
        lockedUser.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        lockedUser.setFailedLoginAttempts(5);

        when(userRepository.findById(5L)).thenReturn(Optional.of(lockedUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.unlockAccount(5L);

        assertNull(lockedUser.getLockedUntil());
        assertEquals(0, lockedUser.getFailedLoginAttempts());
        verify(auditService).logAction(eq("UNLOCK_ACCOUNT"), eq("User"), eq(5L), anyString(), anyString(), eq("Account unlocked by admin"));
    }
}
