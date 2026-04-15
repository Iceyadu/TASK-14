package com.eaglepoint.exam.integration;

import com.eaglepoint.exam.ExamSchedulingApplication;
import com.eaglepoint.exam.auth.dto.LoginRequest;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Broad API smoke for major shipped modules using real auth + MockMvc.
 * This is intentionally no-mock at the controller/service boundary.
 */
@SpringBootTest(
        classes = ExamSchedulingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "integration"})
class ApiSurfaceSmokeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    private static final String ADMIN_USERNAME = "admin_api_surface_test";
    private static final String ADMIN_PASSWORD = "Test@12345678";
    private static final String DEVICE_FP = "device-api-surface-admin";

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        seedAdminIfAbsent();
    }

    private void seedAdminIfAbsent() {
        userRepository.findByUsername(ADMIN_USERNAME).orElseGet(() -> {
            User user = new User();
            user.setUsername(ADMIN_USERNAME);
            user.setPasswordHash(new BCryptPasswordEncoder(4).encode(ADMIN_PASSWORD));
            user.setFullName("API Surface Admin");
            user.setRole(Role.ADMIN);
            user.setAllowConcurrentSessions(true);
            user.setFailedLoginAttempts(0);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(user);
        });
    }

    private String loginAndGetToken() throws Exception {
        LoginRequest loginRequest = new LoginRequest(ADMIN_USERNAME, ADMIN_PASSWORD, DEVICE_FP);
        String body = objectMapper.writeValueAsString(loginRequest);
        String responseBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        return json.get("data").get("sessionToken").asText();
    }

    private void assertOk(String token, MockHttpServletRequestBuilder req) throws Exception {
        mockMvc.perform(req.header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void assertPaginatedOk(String token, String uri) throws Exception {
        mockMvc.perform(get(uri)
                        .param("page", "1")
                        .param("limit", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.pagination.page").exists())
                .andExpect(jsonPath("$.pagination.size").exists())
                .andExpect(jsonPath("$.pagination.total").exists());
    }

    @Test
    void testUnauthorizedAccessIsBlocked() throws Exception {
        mockMvc.perform(get("/api/users").param("page", "1").param("limit", "20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testMajorApiSurfaceAsAdmin() throws Exception {
        String token = loginAndGetToken();

        // Core backend surfaces backing broad UI modules.
        assertPaginatedOk(token, "/api/users");
        assertOk(token, get("/api/campuses"));
        assertPaginatedOk(token, "/api/jobs");
        assertPaginatedOk(token, "/api/audit");
        assertPaginatedOk(token, "/api/anticheat/flags");
        assertPaginatedOk(token, "/api/notifications");
        assertPaginatedOk(token, "/api/exam-sessions");
        assertPaginatedOk(token, "/api/rosters");
        assertPaginatedOk(token, "/api/compliance/reviews");
    }
}
