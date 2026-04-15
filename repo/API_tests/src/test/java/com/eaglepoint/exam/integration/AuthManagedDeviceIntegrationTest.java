package com.eaglepoint.exam.integration;

import com.eaglepoint.exam.ExamSchedulingApplication;
import com.eaglepoint.exam.auth.dto.LoginRequest;
import com.eaglepoint.exam.auth.model.ManagedDevice;
import com.eaglepoint.exam.auth.repository.ManagedDeviceRepository;
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

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ExamSchedulingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthManagedDeviceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private ManagedDeviceRepository managedDeviceRepository;

    private static final String USERNAME = "remember_device_test_user";
    private static final String PASSWORD = "Remember@123456";

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        seedUserIfAbsent();
    }

    private void seedUserIfAbsent() {
        userRepository.findByUsername(USERNAME).orElseGet(() -> {
            User user = new User();
            user.setUsername(USERNAME);
            user.setPasswordHash(new BCryptPasswordEncoder(4).encode(PASSWORD));
            user.setFullName("Remember Device User");
            user.setRole(Role.ADMIN);
            user.setAllowConcurrentSessions(true);
            user.setFailedLoginAttempts(0);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(user);
        });
    }

    @Test
    void testRememberDeviceOnlyAppliesForManagedDevice() throws Exception {
        // First login with unmanaged device + remember=true should still be short-lived.
        LoginRequest unmanagedLogin = new LoginRequest(USERNAME, PASSWORD, "fp-unmanaged");
        unmanagedLogin.setRememberDevice(true);
        MvcResult unmanagedResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unmanagedLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").exists())
                .andReturn();
        JsonNode unmanagedJson = objectMapper.readTree(unmanagedResult.getResponse().getContentAsString());
        String unmanagedToken = unmanagedJson.get("data").get("sessionToken").asText();
        Session unmanagedSession = sessionRepository.findBySessionToken(unmanagedToken).orElseThrow();
        assertThat(unmanagedSession.isRememberDevice()).isFalse();
        assertThat(Duration.between(unmanagedSession.getCreatedAt(), unmanagedSession.getExpiresAt()).toMinutes())
                .isLessThanOrEqualTo(35);

        // Register device via signed request.
        managedDeviceRepository.findByDeviceFingerprint("fp-managed")
                .orElseGet(() -> managedDeviceRepository.save(
                        new ManagedDevice("fp-managed", "Managed Lab Device", 1L)));

        // Managed device + remember=true should use extended session.
        LoginRequest managedLogin = new LoginRequest(USERNAME, PASSWORD, "fp-managed");
        managedLogin.setRememberDevice(true);
        MvcResult managedResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(managedLogin)))
                .andExpect(status().isOk())
                .andReturn();
        String managedToken = objectMapper.readTree(managedResult.getResponse().getContentAsString())
                .get("data").get("sessionToken").asText();

        Session managedSession = sessionRepository.findBySessionToken(managedToken).orElseThrow();
        assertThat(managedSession.isRememberDevice()).isTrue();
        assertThat(Duration.between(managedSession.getCreatedAt(), managedSession.getExpiresAt()).toDays())
                .isGreaterThanOrEqualTo(6);
    }
}
