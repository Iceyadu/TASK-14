package com.eaglepoint.exam.integration;

import com.eaglepoint.exam.ExamSchedulingApplication;
import com.eaglepoint.exam.auth.dto.LoginRequest;
import com.eaglepoint.exam.compliance.dto.ReviewDecisionRequest;
import com.eaglepoint.exam.compliance.repository.ComplianceReviewRepository;
import com.eaglepoint.exam.rooms.model.Campus;
import com.eaglepoint.exam.rooms.model.Course;
import com.eaglepoint.exam.rooms.model.Grade;
import com.eaglepoint.exam.rooms.model.Room;
import com.eaglepoint.exam.rooms.model.SchoolClass;
import com.eaglepoint.exam.rooms.repository.CampusRepository;
import com.eaglepoint.exam.rooms.repository.ClassRepository;
import com.eaglepoint.exam.rooms.repository.CourseRepository;
import com.eaglepoint.exam.rooms.repository.GradeRepository;
import com.eaglepoint.exam.rooms.repository.RoomRepository;
import com.eaglepoint.exam.scheduling.dto.CreateExamSessionRequest;
import com.eaglepoint.exam.scheduling.model.ExamSessionStatus;
import com.eaglepoint.exam.scheduling.model.Term;
import com.eaglepoint.exam.scheduling.repository.ExamSessionRepository;
import com.eaglepoint.exam.scheduling.repository.TermRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the exam session lifecycle covering creation, review,
 * approval, publishing, role-based access, and state transition enforcement.
 */
@SpringBootTest(
        classes = ExamSchedulingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "integration"})
class ExamSessionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ExamSessionRepository examSessionRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private UserScopeAssignmentRepository userScopeAssignmentRepository;

    @Autowired
    private ComplianceReviewRepository complianceReviewRepository;

    private static final String COORDINATOR_USERNAME = "coordinator_session_test";
    private static final String ADMIN_USERNAME = "admin_session_test";
    private static final String STUDENT_USERNAME = "student_session_test";
    private static final String COMMON_PASSWORD = "Test@12345678";
    private static final String DEVICE_FINGERPRINT_A = "device-session-001";
    private static final String DEVICE_FINGERPRINT_B = "device-session-002";
    private static final String DEVICE_FINGERPRINT_C = "device-session-003";

    private Long termId;
    private Long campusId;
    private Long gradeId;
    private Long courseId;
    private Long roomId;
    private Long classId;

    @BeforeEach
    void setUp() {
        // Clean up sessions to allow fresh logins
        sessionRepository.deleteAll();
        complianceReviewRepository.deleteAll();

        // Seed reference data if not present
        termId = seedTermIfAbsent();
        campusId = seedCampusIfAbsent();
        gradeId = seedGradeIfAbsent();
        courseId = seedCourseIfAbsent();
        roomId = seedRoomIfAbsent();
        classId = seedClassIfAbsent();

        // Seed users
        seedUserIfAbsent(COORDINATOR_USERNAME, "Test Coordinator", Role.ACADEMIC_COORDINATOR);
        seedUserIfAbsent(ADMIN_USERNAME, "Test Admin", Role.ADMIN);
        seedUserIfAbsent(STUDENT_USERNAME, "Test Student", Role.STUDENT);
        assignCoordinatorCampusScope();
    }

    private void assignCoordinatorCampusScope() {
        userRepository.findByUsername(COORDINATOR_USERNAME).ifPresent(u -> {
            boolean has = userScopeAssignmentRepository.findByUserId(u.getId()).stream()
                    .anyMatch(a -> a.getScopeType() == ScopeType.CAMPUS && campusId.equals(a.getScopeId()));
            if (!has) {
                userScopeAssignmentRepository.save(new UserScopeAssignment(u.getId(), ScopeType.CAMPUS, campusId));
            }
        });
    }

    // ---- Seed helpers ----

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

    private Long seedTermIfAbsent() {
        List<Term> terms = termRepository.findAll();
        if (!terms.isEmpty()) {
            return terms.get(0).getId();
        }
        Term term = new Term();
        term.setName("2026 Spring");
        term.setStartDate(LocalDate.of(2026, 2, 1));
        term.setEndDate(LocalDate.of(2026, 7, 31));
        term.setIsActive(true);
        return termRepository.save(term).getId();
    }

    private Long seedCampusIfAbsent() {
        List<Campus> campuses = campusRepository.findAll();
        if (!campuses.isEmpty()) {
            return campuses.get(0).getId();
        }
        Campus campus = new Campus();
        campus.setName("Main Campus");
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

    private Long seedCourseIfAbsent() {
        List<Course> courses = courseRepository.findAll();
        if (!courses.isEmpty()) {
            return courses.get(0).getId();
        }
        Course course = new Course();
        course.setName("Mathematics");
        course.setGradeId(gradeId);
        return courseRepository.save(course).getId();
    }

    private Long seedRoomIfAbsent() {
        List<Room> rooms = roomRepository.findAll();
        if (!rooms.isEmpty()) {
            return rooms.get(0).getId();
        }
        Room room = new Room();
        room.setName("Room 101");
        room.setCapacity(40);
        room.setCampusId(campusId);
        return roomRepository.save(room).getId();
    }

    private Long seedClassIfAbsent() {
        List<SchoolClass> classes = classRepository.findAll();
        if (!classes.isEmpty()) {
            return classes.get(0).getId();
        }
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setName("Class 7A");
        schoolClass.setCampusId(campusId);
        schoolClass.setGradeId(gradeId);
        return classRepository.save(schoolClass).getId();
    }

    // ---- Auth helpers ----

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

    private CreateExamSessionRequest buildCreateRequest() {
        CreateExamSessionRequest request = new CreateExamSessionRequest();
        request.setName("Midterm Math Exam");
        request.setTermId(termId);
        request.setCourseId(courseId);
        request.setCampusId(campusId);
        request.setScheduledDate(LocalDate.now().plusDays(30));
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(11, 0));
        request.setRoomId(roomId);
        request.setClassIds(List.of(classId));
        return request;
    }

    @Test
    void testFullSessionLifecycle() throws Exception {
        // Step 1: Login as Academic Coordinator
        String coordinatorToken = login(COORDINATOR_USERNAME, DEVICE_FINGERPRINT_A);

        // Step 2: Create exam session -> DRAFT
        CreateExamSessionRequest createRequest = buildCreateRequest();
        String createBody = objectMapper.writeValueAsString(createRequest);

        MvcResult createResult = mockMvc.perform(post("/api/exam-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long sessionId = createJson.get("data").get("id").asLong();

        // Step 3: Submit for compliance review -> SUBMITTED_FOR_COMPLIANCE_REVIEW
        mockMvc.perform(post("/api/exam-sessions/" + sessionId + "/submit-review")
                        .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED_FOR_COMPLIANCE_REVIEW"));

        // Step 4: Login as Admin
        // Clear session for coordinator first to avoid concurrent session conflict
        sessionRepository.deleteAll();
        String adminToken = login(ADMIN_USERNAME, DEVICE_FINGERPRINT_B);

        // Step 5: Check pending compliance reviews
        MvcResult reviewsResult = mockMvc.perform(get("/api/compliance/reviews")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode reviewsJson = objectMapper.readTree(reviewsResult.getResponse().getContentAsString());
        JsonNode reviewsList = reviewsJson.get("data");
        assertThat(reviewsList.size()).isGreaterThan(0);

        // Get the review ID for approval
        Long reviewId = reviewsList.get(0).get("id").asLong();

        // Step 6: Approve the compliance review
        ReviewDecisionRequest approveRequest = new ReviewDecisionRequest();
        approveRequest.setComment("Approved for publication");
        String approveBody = objectMapper.writeValueAsString(approveRequest);

        mockMvc.perform(post("/api/compliance/reviews/" + reviewId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveBody)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Step 7: Publish the session (admin can also publish)
        mockMvc.perform(post("/api/exam-sessions/" + sessionId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // Step 8: Login as Student
        sessionRepository.deleteAll();
        String studentToken = login(STUDENT_USERNAME, DEVICE_FINGERPRINT_C);

        // Step 9: Verify published session visible in student schedule
        mockMvc.perform(get("/api/exam-sessions/student/schedule")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void testPublishBlockedWithoutApproval() throws Exception {
        // Login as Academic Coordinator
        String coordinatorToken = login(COORDINATOR_USERNAME, DEVICE_FINGERPRINT_A);

        // Create session in DRAFT
        CreateExamSessionRequest createRequest = buildCreateRequest();
        createRequest.setName("Blocked Publish Exam");
        String createBody = objectMapper.writeValueAsString(createRequest);

        MvcResult createResult = mockMvc.perform(post("/api/exam-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long sessionId = createJson.get("data").get("id").asLong();

        // Attempt to publish without approval -> expect 409 Conflict
        mockMvc.perform(post("/api/exam-sessions/" + sessionId + "/publish")
                        .header("Authorization", "Bearer " + coordinatorToken))
                .andExpect(status().isConflict());
    }

    @Test
    void testStudentCannotCreateSession() throws Exception {
        // Login as Student
        String studentToken = login(STUDENT_USERNAME, DEVICE_FINGERPRINT_C);

        // Attempt to create exam session -> expect 403 Forbidden
        CreateExamSessionRequest createRequest = buildCreateRequest();
        createRequest.setName("Student Attempt Exam");
        String createBody = objectMapper.writeValueAsString(createRequest);

        mockMvc.perform(post("/api/exam-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }
}
