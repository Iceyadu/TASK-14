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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ExamSchedulingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignedMutationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    private static final String ADMIN_USERNAME = "admin_signed_mut_test";
    private static final String ADMIN_PASSWORD = "Admin@12345678";
    private static final String DEVICE_FP = "device-signed-mutations";

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
            user.setFullName("Signed Mutation Admin");
            user.setRole(Role.ADMIN);
            user.setAllowConcurrentSessions(true);
            user.setFailedLoginAttempts(0);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(user);
        });
    }

    private String[] loginAdminWithSigning() throws Exception {
        LoginRequest loginRequest = new LoginRequest(ADMIN_USERNAME, ADMIN_PASSWORD, DEVICE_FP);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").exists())
                .andExpect(jsonPath("$.data.signingKey").exists())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new String[] {
                json.get("data").get("sessionToken").asText(),
                json.get("data").get("signingKey").asText()
        };
    }

    @Test
    void testUnsignedMutationsRejectedButSignedMutationsAccepted() throws Exception {
        String[] creds = loginAdminWithSigning();
        String token = creds[0];
        String signingKey = creds[1];

        String campusName = "Signed Campus " + System.currentTimeMillis();
        String campusBody = objectMapper.writeValueAsString(Map.of("name", campusName));

        // Same authenticated mutation without signing headers should be rejected.
        mockMvc.perform(post("/api/campuses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campusBody))
                .andExpect(status().isUnauthorized());

        // Signed mutation must pass.
        MockHttpServletRequestBuilder signedCampusCreate = SigningTestHelper.sign(
                post("/api/campuses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campusBody),
                signingKey, "POST", "/api/campuses", campusBody);
        MvcResult campusResult = mockMvc.perform(signedCampusCreate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        Long campusId = objectMapper.readTree(campusResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String username = "signed_user_" + System.currentTimeMillis();
        String userCreateBody = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "password", "Student@123456",
                "fullName", "Signed User",
                "role", "STUDENT"
        ));

        MockHttpServletRequestBuilder signedUserCreate = SigningTestHelper.sign(
                post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userCreateBody),
                signingKey, "POST", "/api/users", userCreateBody);
        MvcResult userResult = mockMvc.perform(signedUserCreate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andReturn();

        Long userId = objectMapper.readTree(userResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        String concurrentBody = "{\"allowed\":true}";
        MockHttpServletRequestBuilder signedConcurrentToggle = SigningTestHelper.sign(
                put("/api/users/" + userId + "/concurrent-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(concurrentBody),
                signingKey, "PUT", "/api/users/" + userId + "/concurrent-sessions", concurrentBody);
        mockMvc.perform(signedConcurrentToggle)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowConcurrentSessions").value(true));

        String campusUpdateBody = objectMapper.writeValueAsString(Map.of(
                "id", campusId,
                "name", "Signed Campus Updated",
                "status", "ACTIVE"
        ));
        MockHttpServletRequestBuilder signedCampusUpdate = SigningTestHelper.sign(
                put("/api/campuses/" + campusId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(campusUpdateBody),
                signingKey, "PUT", "/api/campuses/" + campusId, campusUpdateBody);
        mockMvc.perform(signedCampusUpdate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Signed Campus Updated"));
    }
}
