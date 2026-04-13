package com.eaglepoint.exam.auth.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.auth.dto.LoginRequest;
import com.eaglepoint.exam.auth.dto.LoginResponse;
import com.eaglepoint.exam.auth.model.PasswordHistory;
import com.eaglepoint.exam.auth.repository.ManagedDeviceRepository;
import com.eaglepoint.exam.auth.repository.PasswordHistoryRepository;
import com.eaglepoint.exam.security.model.Session;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.repository.SessionRepository;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.dto.FieldError;
import com.eaglepoint.exam.shared.enums.Permission;
import com.eaglepoint.exam.shared.enums.RolePermissions;
import com.eaglepoint.exam.shared.exception.AccountLockedException;
import com.eaglepoint.exam.shared.exception.ConcurrentSessionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Handles authentication, session management, password policy enforcement,
 * and account lockout logic.
 */
@Service
public class AuthService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final ManagedDeviceRepository managedDeviceRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final AuditService auditService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${app.signing-secret}")
    private String appSigningSecret;

    @Value("${app.security.max-failed-login-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.security.lockout-duration-minutes:15}")
    private int lockoutMinutes;

    @Value("${app.security.session-timeout-minutes:30}")
    private int shortSessionMinutes;

    @Value("${app.security.remember-device-days:7}")
    private int rememberDeviceDays;

    public AuthService(UserRepository userRepository,
                       SessionRepository sessionRepository,
                       ManagedDeviceRepository managedDeviceRepository,
                       PasswordHistoryRepository passwordHistoryRepository,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.managedDeviceRepository = managedDeviceRepository;
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.auditService = auditService;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
    }

    /**
     * Authenticates a user and creates a new session.
     * Failed password attempts and lockout must commit even though we throw auth exceptions.
     */
    @Transactional(noRollbackFor = {
            org.springframework.security.authentication.BadCredentialsException.class,
            AccountLockedException.class
    })
    public LoginResponse login(LoginRequest request) {
        // 1. Find user by username
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid username or password"));

        // 2. Check lockout
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountLockedException(user.getLockedUntil());
        }

        // 3. Validate password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // 4. On failure: increment attempts, possibly lock
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);

            if (attempts >= maxFailedAttempts) {
                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(lockoutMinutes);
                user.setLockedUntil(lockUntil);
                user.setUpdatedAt(LocalDateTime.now());
                userRepository.save(user);

                auditService.logAction("ACCOUNT_LOCKED", "User", user.getId(),
                        null, null,
                        "Account locked after " + attempts + " failed attempts until " + lockUntil);

                throw new AccountLockedException(lockUntil);
            }

            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid username or password");
        }

        // 5. On success: reset failed attempts
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // 6. Check concurrent sessions
        List<Session> existingSessions = sessionRepository.findByUserId(user.getId());
        List<Session> activeSessions = existingSessions.stream()
                .filter(s -> s.getExpiresAt().isAfter(LocalDateTime.now()))
                .toList();

        for (Session activeSession : activeSessions) {
            if (!request.getDeviceFingerprint().equals(activeSession.getDeviceFingerprint())) {
                if (!user.isAllowConcurrentSessions()) {
                    throw new ConcurrentSessionException();
                }
            } else {
                // 7. Same device: invalidate old session
                sessionRepository.delete(activeSession);
            }
        }

        // 8. Generate session token
        String sessionToken = UUID.randomUUID().toString();

        // 9. Generate signing key: HMAC-SHA256(APP_SIGNING_SECRET, sessionToken + userId)
        String signingKey = generateSigningKey(sessionToken, user.getId());

        // 10. Determine expiry: optional 7-day remember only when explicitly requested on a managed device
        boolean isManagedDevice = managedDeviceRepository.existsByDeviceFingerprint(request.getDeviceFingerprint());
        boolean wantRemember = Boolean.TRUE.equals(request.getRememberDevice());
        LocalDateTime expiresAt;
        boolean rememberDevice = false;

        if (wantRemember && isManagedDevice) {
            expiresAt = LocalDateTime.now().plusDays(rememberDeviceDays);
            rememberDevice = true;
        } else {
            expiresAt = LocalDateTime.now().plusMinutes(shortSessionMinutes);
        }

        // 11. Create and save session
        Session session = new Session();
        session.setSessionToken(sessionToken);
        session.setUserId(user.getId());
        session.setDeviceFingerprint(request.getDeviceFingerprint());
        session.setSigningKey(signingKey);
        session.setCreatedAt(LocalDateTime.now());
        session.setLastActiveAt(LocalDateTime.now());
        session.setExpiresAt(expiresAt);
        session.setRememberDevice(rememberDevice);
        sessionRepository.save(session);

        // 12. Audit login event
        auditService.logAction("LOGIN", "Session", session.getId(),
                null, null,
                "User logged in from device: " + request.getDeviceFingerprint());

        // 13. Build and return response
        List<Permission> permissions = new ArrayList<>(RolePermissions.getPermissions(user.getRole()));

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                permissions
        );

        return new LoginResponse(sessionToken, signingKey, expiresAt, userInfo);
    }

    /**
     * Terminates the current user's session.
     */
    @Transactional
    public void logout() {
        String sessionId = RequestContext.getSessionId();
        if (sessionId != null) {
            sessionRepository.findBySessionToken(sessionId)
                    .ifPresent(session -> {
                        sessionRepository.delete(session);
                        auditService.logAction("LOGOUT", "Session", session.getId(),
                                null, null, "User logged out");
                    });
        }
    }

    /**
     * Validates password complexity requirements.
     *
     * @return list of validation errors (empty if valid)
     */
    public List<FieldError> validatePasswordComplexity(String password) {
        List<FieldError> errors = new ArrayList<>();

        if (password == null || password.length() < 12) {
            errors.add(new FieldError("password", "Password must be at least 12 characters long"));
        }

        if (password != null) {
            if (!password.matches(".*[A-Z].*")) {
                errors.add(new FieldError("password", "Password must contain at least one uppercase letter"));
            }
            if (!password.matches(".*[a-z].*")) {
                errors.add(new FieldError("password", "Password must contain at least one lowercase letter"));
            }
            if (!password.matches(".*\\d.*")) {
                errors.add(new FieldError("password", "Password must contain at least one digit"));
            }
            if (!password.matches(".*[^A-Za-z0-9].*")) {
                errors.add(new FieldError("password", "Password must contain at least one special character"));
            }
        }

        return errors;
    }

    /**
     * Checks whether the given password has been used recently by the user.
     *
     * @return list containing a single error if password was recently used, empty otherwise
     */
    public List<FieldError> checkPasswordHistory(Long userId, String newPassword) {
        List<FieldError> errors = new ArrayList<>();
        List<PasswordHistory> recentPasswords = passwordHistoryRepository
                .findTop5ByUserIdOrderByCreatedAtDesc(userId);

        for (PasswordHistory history : recentPasswords) {
            if (passwordEncoder.matches(newPassword, history.getPasswordHash())) {
                errors.add(new FieldError("password", "Password has been used recently. Please choose a different password."));
                break;
            }
        }

        return errors;
    }

    /**
     * Terminates all sessions for a given user. Admin-only operation.
     */
    @Transactional
    public void terminateSession(Long userId) {
        sessionRepository.deleteByUserId(userId);
        auditService.logAction("TERMINATE_SESSIONS", "User", userId,
                null, null, "All sessions terminated by admin");
    }

    /**
     * Unlocks a locked user account. Admin-only operation.
     */
    @Transactional
    public void unlockAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new com.eaglepoint.exam.shared.exception.EntityNotFoundException("User", userId));

        String oldState = "lockedUntil=" + user.getLockedUntil() + ", failedAttempts=" + user.getFailedLoginAttempts();

        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        auditService.logAction("UNLOCK_ACCOUNT", "User", userId,
                oldState, "lockedUntil=null, failedAttempts=0",
                "Account unlocked by admin");
    }

    /**
     * Returns the BCrypt password encoder (cost factor 12) used by this service.
     */
    public BCryptPasswordEncoder getPasswordEncoder() {
        return passwordEncoder;
    }

    /**
     * Generates an HMAC-SHA256 signing key for the given session token and user ID.
     */
    private String generateSigningKey(String sessionToken, Long userId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    appSigningSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal((sessionToken + userId).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate signing key", e);
        }
    }
}
