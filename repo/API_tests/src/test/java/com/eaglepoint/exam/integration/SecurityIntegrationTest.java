package com.eaglepoint.exam.integration;

import com.eaglepoint.exam.ExamSchedulingApplication;
import com.eaglepoint.exam.auth.dto.LoginRequest;
import com.eaglepoint.exam.security.model.Session;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.repository.SessionRepository;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.shared.enums.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for security enforcement including unauthenticated access,
 * session expiration, account lockout after failed attempts, and concurrent
 * session blocking.
 */
@SpringBootTest(
        classes = ExamSchedulingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    private static final String SECURITY_USER = "security_test_user";
    private static final String LOCKOUT_USER = "lockout_test_user";
    private static final String CONCURRENT_USER = "concurrent_test_user";
    private static final String COMMON_PASSWORD = "Test@12345678";
    private static final String DEVICE_FP_A = "device-security-a";
    private static final String DEVICE_FP_B = "device-security-b";

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();

        seedUserIfAbsent(SECURITY_USER, "Security Test User", Role.ACADEMIC_COORDINATOR);
        seedLockoutUser();
        seedConcurrentUser();
    }

    private void seedUserIfAbsent(String username, String fullName, Role role) {
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User();
            user.setUsername(username);
            user.setPasswordHash(new BCryptPasswordEncoder(4).encode(COMMON_PASSWORD));
            user.setFullName(fullName);
            user.setRole(role);
            user.setAllowConcurrentSessions(false);
            user.setFailedLoginAttempts(0);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    private void seedLockoutUser() {
        User user = userRepository.findByUsername(LOCKOUT_USER).orElse(null);
        if (user == null) {
            user = new User();
            user.setUsername(LOCKOUT_USER);
            user.setPasswordHash(new BCryptPasswordEncoder(4).encode(COMMON_PASSWORD));
            user.setFullName("Lockout Test User");
            user.setRole(Role.ACADEMIC_COORDINATOR);
            user.setAllowConcurrentSessions(false);
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
        } else {
            // Reset lockout state before each test
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    private void seedConcurrentUser() {
        if (userRepository.findByUsername(CONCURRENT_USER).isEmpty()) {
            User user = new User();
            user.setUsername(CONCURRENT_USER);
            user.setPasswordHash(new BCryptPasswordEncoder(4).encode(COMMON_PASSWORD));
            user.setFullName("Concurrent Test User");
            user.setRole(Role.ACADEMIC_COORDINATOR);
            user.setAllowConcurrentSessions(false);
            user.setFailedLoginAttempts(0);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    /**
     * Performs login and returns a two-element array: [sessionToken, signingKey].
     */
    private String[] loginExpectingSuccessWithSigningKey(String username, String deviceFingerprint) throws Exception {
        LoginRequest loginRequest = new LoginRequest(username, COMMON_PASSWORD, deviceFingerprint);
        String body = objectMapper.writeValueAsString(loginRequest);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").exists())
                .andExpect(jsonPath("$.data.signingKey").exists())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = json.get("data").get("sessionToken").asText();
        String signingKey = json.get("data").get("signingKey").asText();
        return new String[]{token, signingKey};
    }

    private String loginExpectingSuccess(String username, String deviceFingerprint) throws Exception {
        return loginExpectingSuccessWithSigningKey(username, deviceFingerprint)[0];
    }

    @Test
    void testUnauthenticatedAccess() throws Exception {
        // GET /api/rosters without session token -> 401 Unauthorized
        mockMvc.perform(get("/api/rosters"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testExpiredSessionRejected() throws Exception {
        // Step 1: Login and get token + signing key
        String[] creds = loginExpectingSuccessWithSigningKey(SECURITY_USER, DEVICE_FP_A);
        String token = creds[0];
        String signingKey = creds[1];

        // Step 2: Manually set session last_active_at to 31 minutes ago
        List<Session> sessions = sessionRepository.findAll();
        for (Session session : sessions) {
            if (session.getSessionToken().equals(token)) {
                session.setLastActiveAt(LocalDateTime.now().minusMinutes(31));
                session.setExpiresAt(LocalDateTime.now().minusMinutes(1));
                sessionRepository.save(session);
                break;
            }
        }

        // Step 3: Request with expired session + valid signing -> 401 Unauthorized
        // SecurityFilter should reject before RequestSigningFilter runs
        mockMvc.perform(SigningTestHelper.sign(
                        get("/api/rosters")
                                .header("Authorization", "Bearer " + token),
                        signingKey, "GET", "/api/rosters", ""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAccountLockoutAfterFailedAttempts() throws Exception {
        String wrongPassword = "WrongPassword123!";

        // Four failed attempts -> 401; fifth failure hits max attempts and locks the account -> 423
        for (int i = 0; i < 4; i++) {
            LoginRequest badRequest = new LoginRequest(LOCKOUT_USER, wrongPassword, DEVICE_FP_A);
            String body = objectMapper.writeValueAsString(badRequest);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        LoginRequest fifthBad = new LoginRequest(LOCKOUT_USER, wrongPassword, DEVICE_FP_A);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fifthBad)))
                .andExpect(status().isLocked());

        // Correct password while still locked -> 423 Locked
        LoginRequest correctRequest = new LoginRequest(LOCKOUT_USER, COMMON_PASSWORD, DEVICE_FP_A);
        String correctBody = objectMapper.writeValueAsString(correctRequest);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctBody))
                .andExpect(status().isLocked());

        // Manually reset locked_until to simulate 15 minutes passing
        User lockedUser = userRepository.findByUsername(LOCKOUT_USER).orElseThrow();
        lockedUser.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        lockedUser.setFailedLoginAttempts(0);
        lockedUser.setUpdatedAt(LocalDateTime.now());
        userRepository.save(lockedUser);

        // Now login should succeed
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").exists());
    }

    @Test
    void testConcurrentSessionBlocked() throws Exception {
        // Login from device A -> success
        String tokenA = loginExpectingSuccess(CONCURRENT_USER, DEVICE_FP_A);
        assertThat(tokenA).isNotBlank();

        // Login from device B -> 409 Conflict (concurrent session blocked)
        LoginRequest loginB = new LoginRequest(CONCURRENT_USER, COMMON_PASSWORD, DEVICE_FP_B);
        String bodyB = objectMapper.writeValueAsString(loginB);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyB))
                .andExpect(status().isConflict());
    }
}
