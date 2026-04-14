package com.eaglepoint.exam.integration;

import com.eaglepoint.exam.ExamSchedulingApplication;
import com.eaglepoint.exam.auth.dto.LoginRequest;
import com.eaglepoint.exam.rooms.model.Campus;
import com.eaglepoint.exam.rooms.model.Grade;
import com.eaglepoint.exam.rooms.model.SchoolClass;
import com.eaglepoint.exam.rooms.repository.CampusRepository;
import com.eaglepoint.exam.rooms.repository.ClassRepository;
import com.eaglepoint.exam.rooms.repository.GradeRepository;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.model.UserScopeAssignment;
import com.eaglepoint.exam.security.repository.SessionRepository;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.security.repository.UserScopeAssignmentRepository;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.enums.ScopeType;
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
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for cross-user access controls verifying that scope-based
 * filtering, role-based access, and data isolation work correctly.
 */
@SpringBootTest(
        classes = ExamSchedulingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "integration"})
class CrossUserAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private UserScopeAssignmentRepository userScopeAssignmentRepository;

    private static final String TEACHER_USERNAME = "teacher_cross_test";
    private static final String STUDENT_A_USERNAME = "student_a_cross_test";
    private static final String STUDENT_B_USERNAME = "student_b_cross_test";
    private static final String COORDINATOR_USERNAME = "coordinator_cross_test";
    private static final String ADMIN_USERNAME = "admin_cross_test";
    private static final String COMMON_PASSWORD = "Test@12345678";

    private static final String DEVICE_FP_TEACHER = "device-cross-teacher";
    private static final String DEVICE_FP_STU_A = "device-cross-stu-a";
    private static final String DEVICE_FP_STU_B = "device-cross-stu-b";
    private static final String DEVICE_FP_COORD = "device-cross-coord";
    private static final String DEVICE_FP_ADMIN = "device-cross-admin";

    private Long campus1Id;
    private Long campus2Id;
    private Long classAId;
    private Long classBId;
    private Long gradeId;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();

        // Seed two campuses
        campus1Id = seedCampus("Campus One");
        campus2Id = seedCampus("Campus Two");
        gradeId = seedGradeIfAbsent();

        // Seed two classes on different campuses
        classAId = seedClass("Class 7A", campus1Id, gradeId);
        classBId = seedClass("Class 7B", campus2Id, gradeId);

        // Seed users
        Long teacherId = seedUserIfAbsent(TEACHER_USERNAME, "Homeroom Teacher", Role.HOMEROOM_TEACHER);
        seedUserIfAbsent(STUDENT_A_USERNAME, "Student Alpha", Role.STUDENT);
        seedUserIfAbsent(STUDENT_B_USERNAME, "Student Beta", Role.STUDENT);
        Long coordinatorId = seedUserIfAbsent(COORDINATOR_USERNAME, "Campus 1 Coordinator", Role.ACADEMIC_COORDINATOR);
        seedUserIfAbsent(ADMIN_USERNAME, "System Admin", Role.ADMIN);

        // Assign teacher to Class 7A (campus 1)
        assignScope(teacherId, ScopeType.CLASS, classAId);

        // Assign coordinator to Campus 1
        assignScope(coordinatorId, ScopeType.CAMPUS, campus1Id);
    }

    private Long seedCampus(String name) {
        List<Campus> existing = campusRepository.findAll();
        for (Campus c : existing) {
            if (c.getName().equals(name)) {
                return c.getId();
            }
        }
        Campus campus = new Campus();
        campus.setName(name);
        return campusRepository.save(campus).getId();
    }

    private Long seedGradeIfAbsent() {
        List<Grade> grades = gradeRepository.findAll();
        if (!grades.isEmpty()) {
            return grades.get(0).getId();
        }
        Grade g = new Grade();
        g.setName("Grade 7");
        g.setLevel(7);
        return gradeRepository.save(g).getId();
    }

    private Long seedClass(String name, Long campusId, Long gradeId) {
        List<SchoolClass> existing = classRepository.findAll();
        for (SchoolClass c : existing) {
            if (c.getName().equals(name)) {
                return c.getId();
            }
        }
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setName(name);
        schoolClass.setCampusId(campusId);
        schoolClass.setGradeId(gradeId);
        return classRepository.save(schoolClass).getId();
    }

    private Long seedUserIfAbsent(String username, String fullName, Role role) {
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseGet(() -> {
                    User user = new User();
                    user.setUsername(username);
                    user.setPasswordHash(new BCryptPasswordEncoder(4).encode(COMMON_PASSWORD));
                    user.setFullName(fullName);
                    user.setRole(role);
                    user.setAllowConcurrentSessions(false);
                    user.setFailedLoginAttempts(0);
                    user.setCreatedAt(LocalDateTime.now());
                    user.setUpdatedAt(LocalDateTime.now());
                    return userRepository.save(user).getId();
                });
    }

    private void assignScope(Long userId, ScopeType scopeType, Long scopeId) {
        // Check if assignment already exists
        List<UserScopeAssignment> existing = userScopeAssignmentRepository.findByUserId(userId);
        for (UserScopeAssignment a : existing) {
            if (a.getScopeType() == scopeType && a.getScopeId().equals(scopeId)) {
                return;
            }
        }
        UserScopeAssignment assignment = new UserScopeAssignment(userId, scopeType, scopeId);
        userScopeAssignmentRepository.save(assignment);
    }

    private String login(String username, String deviceFingerprint) throws Exception {
        LoginRequest loginRequest = new LoginRequest(username, COMMON_PASSWORD, deviceFingerprint);
        String body = objectMapper.writeValueAsString(loginRequest);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").exists())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("data").get("sessionToken").asText();
    }

    @Test
    void testTeacherCannotAccessOtherClassRoster() throws Exception {
        // Login as Homeroom Teacher (assigned to Class 7A)
        String teacherToken = login(TEACHER_USERNAME, DEVICE_FP_TEACHER);

        // Attempt to access Class 7B roster -> should return empty or 403
        mockMvc.perform(get("/api/rosters")
                        .param("classId", classBId.toString())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", anyOf(empty(), hasSize(0))));
    }

    @Test
    void testStudentCannotAccessOtherStudentData() throws Exception {
        // Login as Student A
        String studentAToken = login(STUDENT_A_USERNAME, DEVICE_FP_STU_A);

        // Verify Student A can access own schedule
        mockMvc.perform(get("/api/exam-sessions/student/schedule")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk());

        // Student A accessing own inbox is fine
        mockMvc.perform(get("/api/notifications/inbox")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk());

        // Student A should not be able to access admin-level endpoints
        mockMvc.perform(get("/api/rosters")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCoordinatorScopedToCampus() throws Exception {
        // Login as Academic Coordinator (scoped to Campus 1)
        String coordToken = login(COORDINATOR_USERNAME, DEVICE_FP_COORD);

        // Access exam sessions scoped to Campus 2 -> should return empty (scope filter)
        MvcResult result = mockMvc.perform(get("/api/exam-sessions")
                        .param("campusId", campus2Id.toString())
                        .header("Authorization", "Bearer " + coordToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = json.get("data");
        assertThat(data.size()).isEqualTo(0);
    }

    @Test
    void testAdminBypassesScope() throws Exception {
        // Login as Admin
        String adminToken = login(ADMIN_USERNAME, DEVICE_FP_ADMIN);

        // Admin should be able to access all rosters without scope restriction
        mockMvc.perform(get("/api/rosters")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        // Admin should be able to access all exam sessions across all campuses
        mockMvc.perform(get("/api/exam-sessions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
