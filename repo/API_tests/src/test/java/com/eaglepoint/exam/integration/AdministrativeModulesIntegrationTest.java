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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
@ActiveProfiles({"test", "integration"})
class AdministrativeModulesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    private static final String ADMIN_USERNAME = "admin_module_test";
    private static final String ADMIN_PASSWORD = "Admin@12345678";
    private static final String TEACHER_USERNAME = "teacher_module_test";
    private static final String TEACHER_PASSWORD = "Teacher@12345678";
    private static final String DEVICE_FP = "device-admin-module";

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        seedAdminIfAbsent();
        seedTeacherIfAbsent();
    }

    private void seedAdminIfAbsent() {
        userRepository.findByUsername(ADMIN_USERNAME).orElseGet(() -> {
            User user = new User();
            user.setUsername(ADMIN_USERNAME);
            user.setPasswordHash(new BCryptPasswordEncoder(4).encode(ADMIN_PASSWORD));
            user.setFullName("Admin Module Test");
            user.setRole(Role.ADMIN);
            user.setAllowConcurrentSessions(true);
            user.setFailedLoginAttempts(0);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(user);
        });
    }

    private String loginAdmin() throws Exception {
        return login(ADMIN_USERNAME, ADMIN_PASSWORD, DEVICE_FP);
    }

    private String login(String username, String password, String deviceFingerprint) throws Exception {
        LoginRequest request = new LoginRequest(username, password, deviceFingerprint);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").exists())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("data").get("sessionToken").asText();
    }

    private void seedTeacherIfAbsent() {
        userRepository.findByUsername(TEACHER_USERNAME).orElseGet(() -> {
            User user = new User();
            user.setUsername(TEACHER_USERNAME);
            user.setPasswordHash(new BCryptPasswordEncoder(4).encode(TEACHER_PASSWORD));
            user.setFullName("Teacher Module Test");
            user.setRole(Role.HOMEROOM_TEACHER);
            user.setAllowConcurrentSessions(true);
            user.setFailedLoginAttempts(0);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(user);
        });
    }

    @Test
    void testUsersValidationCreateAndConcurrentToggle() throws Exception {
        String token = loginAdmin();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "password": "short",
                                  "fullName": "",
                                  "role": "STUDENT"
                                }
                                """))
                .andExpect(status().isBadRequest());

        String username = "api_user_" + System.currentTimeMillis();
        MvcResult createResult = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "Student@123456",
                                  "fullName": "API Student",
                                  "role": "STUDENT"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.fullName").value("API Student"))
                .andReturn();

        Long userId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(put("/api/users/" + userId + "/concurrent-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allowed\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowConcurrentSessions").value(true));
    }

    @Test
    void testCampusAndRoomCrudBehavior() throws Exception {
        String token = loginAdmin();

        String campusName = "E2E Campus " + System.currentTimeMillis();
        MvcResult createCampus = mockMvc.perform(post("/api/campuses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s"
                                }
                                """.formatted(campusName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(campusName))
                .andReturn();

        Long campusId = objectMapper.readTree(createCampus.getResponse().getContentAsString()).get("data").get("id").asLong();

        String roomName = "E2E Room " + System.currentTimeMillis();
        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "campusId": %d,
                                  "capacity": 30
                                }
                                """.formatted(roomName, campusId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(roomName))
                .andExpect(jsonPath("$.data.campusId").value(campusId));

        MvcResult roomList = mockMvc.perform(get("/api/rooms")
                        .param("campusId", String.valueOf(campusId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();
        String roomsJson = roomList.getResponse().getContentAsString();
        assertThat(roomsJson).contains(roomName);
    }

    @Test
    void testAuthDeviceAndAdminSessionEndpoints() throws Exception {
        String token = loginAdmin();

        MvcResult deviceResult = mockMvc.perform(post("/api/auth/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceFingerprint": "fp-%d",
                                  "description": "Integration Device"
                                }
                                """.formatted(System.currentTimeMillis())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        Long deviceId = objectMapper.readTree(deviceResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(get("/api/auth/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(delete("/api/auth/devices/" + deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Long adminId = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow().getId();
        mockMvc.perform(post("/api/auth/sessions/" + adminId + "/terminate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Session termination invalidates current token; re-login for subsequent admin action.
        String tokenAfterTerminate = loginAdmin();
        mockMvc.perform(post("/api/auth/users/" + adminId + "/unlock")
                        .header("Authorization", "Bearer " + tokenAfterTerminate))
                .andExpect(status().isOk());
    }

    @Test
    void testJobsAuditAntiCheatProctorAndReferenceEndpointDepth() throws Exception {
        String token = loginAdmin();

        mockMvc.perform(get("/api/jobs")
                        .param("page", "1")
                        .param("limit", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.pagination.total").exists());

        mockMvc.perform(post("/api/jobs/999999999/rerun")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/audit")
                        .param("page", "0")
                        .param("size", "20")
                        .param("action", "USER_CREATE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.pagination.page").value(0));

        mockMvc.perform(post("/api/anticheat/flags/1/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"\",\"comment\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/proctor-assignments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/terms").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        mockMvc.perform(get("/api/grades").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        mockMvc.perform(get("/api/classes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        mockMvc.perform(get("/api/courses").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void testNonAdminPermissionBoundariesOnAdminModules() throws Exception {
        String teacherToken = login(TEACHER_USERNAME, TEACHER_PASSWORD, "device-teacher-module");

        mockMvc.perform(get("/api/jobs")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/audit")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/campuses")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Forbidden Teacher Campus\"}"))
                .andExpect(status().isForbidden());
    }
}
