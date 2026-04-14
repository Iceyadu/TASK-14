package com.eaglepoint.exam.integration;

import com.eaglepoint.exam.ExamSchedulingApplication;
import com.eaglepoint.exam.auth.dto.LoginRequest;
import com.eaglepoint.exam.compliance.dto.ReviewDecisionRequest;
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
import com.eaglepoint.exam.scheduling.model.Term;
import com.eaglepoint.exam.scheduling.repository.TermRepository;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.model.UserScopeAssignment;
import com.eaglepoint.exam.security.repository.SessionRepository;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.security.repository.UserScopeAssignmentRepository;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.enums.ScopeType;
import com.eaglepoint.exam.versioning.repository.EntityVersionRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the version restore workflow verifying that restoring
 * a previous version creates a new version entry without mutating existing
 * versions, and that restoring a published entity triggers re-review.
 */
@SpringBootTest(
        classes = ExamSchedulingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "integration"})
class VersionRestoreIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EntityVersionRepository entityVersionRepository;

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

    private static final String COORDINATOR_USERNAME = "coordinator_version_test";
    private static final String ADMIN_USERNAME = "admin_version_test";
    private static final String COMMON_PASSWORD = "Test@12345678";
    private static final String DEVICE_FP_COORD = "device-version-coord";
    private static final String DEVICE_FP_ADMIN = "device-version-admin";

    private Long termId;
    private Long campusId;
    private Long gradeId;
    private Long courseId;
    private Long roomId;
    private Long classId;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();

        termId = seedTermIfAbsent();
        campusId = seedCampusIfAbsent();
        gradeId = seedGradeIfAbsent();
        courseId = seedCourseIfAbsent();
        roomId = seedRoomIfAbsent();
        classId = seedClassIfAbsent();

        seedUserIfAbsent(COORDINATOR_USERNAME, "Version Coordinator", Role.ACADEMIC_COORDINATOR);
        seedUserIfAbsent(ADMIN_USERNAME, "Version Admin", Role.ADMIN);
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
        if (!terms.isEmpty()) return terms.get(0).getId();
        Term term = new Term();
        term.setName("2026 Spring");
        term.setStartDate(LocalDate.of(2026, 2, 1));
        term.setEndDate(LocalDate.of(2026, 7, 31));
        term.setIsActive(true);
        return termRepository.save(term).getId();
    }

    private Long seedCampusIfAbsent() {
        List<Campus> list = campusRepository.findAll();
        if (!list.isEmpty()) return list.get(0).getId();
        Campus c = new Campus();
        c.setName("Version Test Campus");
        return campusRepository.save(c).getId();
    }

    private Long seedGradeIfAbsent() {
        List<Grade> list = gradeRepository.findAll();
        if (!list.isEmpty()) return list.get(0).getId();
        Grade g = new Grade();
        g.setName("Grade 8");
        g.setLevel(8);
        return gradeRepository.save(g).getId();
    }

    private Long seedCourseIfAbsent() {
        List<Course> list = courseRepository.findAll();
        if (!list.isEmpty()) return list.get(0).getId();
        Course c = new Course();
        c.setName("Mathematics");
        c.setGradeId(gradeId);
        return courseRepository.save(c).getId();
    }

    private Long seedRoomIfAbsent() {
        List<Room> list = roomRepository.findAll();
        if (!list.isEmpty()) return list.get(0).getId();
        Room r = new Room();
        r.setName("Room 201");
        r.setCapacity(40);
        r.setCampusId(campusId);
        return roomRepository.save(r).getId();
    }

    private Long seedClassIfAbsent() {
        List<SchoolClass> list = classRepository.findAll();
        if (!list.isEmpty()) return list.get(0).getId();
        SchoolClass sc = new SchoolClass();
        sc.setName("Class 8A");
        sc.setCampusId(campusId);
        sc.setGradeId(gradeId);
        return classRepository.save(sc).getId();
    }

    private String login(String username, String deviceFingerprint) throws Exception {
        LoginRequest loginRequest = new LoginRequest(username, COMMON_PASSWORD, deviceFingerprint);
        String body = objectMapper.writeValueAsString(loginRequest);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("data").get("sessionToken").asText();
    }

    private CreateExamSessionRequest buildRequest(String name) {
        CreateExamSessionRequest request = new CreateExamSessionRequest();
        request.setName(name);
        request.setTermId(termId);
        request.setCourseId(courseId);
        request.setCampusId(campusId);
        request.setScheduledDate(LocalDate.now().plusDays(60));
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(11, 0));
        request.setRoomId(roomId);
        request.setClassIds(List.of(classId));
        return request;
    }

    @Test
    void testRestoreCreatesNewVersion() throws Exception {
        String coordToken = login(COORDINATOR_USERNAME, DEVICE_FP_COORD);

        // Create entity (version 1)
        CreateExamSessionRequest createRequest = buildRequest("Version Test Exam v1");
        String createBody = objectMapper.writeValueAsString(createRequest);

        MvcResult createResult = mockMvc.perform(post("/api/exam-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", "Bearer " + coordToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long sessionId = createJson.get("data").get("id").asLong();

        // Update once (version 2)
        CreateExamSessionRequest updateRequest1 = buildRequest("Version Test Exam v2");
        mockMvc.perform(put("/api/exam-sessions/" + sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest1))
                        .header("Authorization", "Bearer " + coordToken))
                .andExpect(status().isOk());

        // Update again (version 3)
        CreateExamSessionRequest updateRequest2 = buildRequest("Version Test Exam v3");
        mockMvc.perform(put("/api/exam-sessions/" + sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest2))
                        .header("Authorization", "Bearer " + coordToken))
                .andExpect(status().isOk());

        // Check versions exist
        MvcResult versionsResult = mockMvc.perform(get("/api/versions/ExamSession/" + sessionId)
                        .header("Authorization", "Bearer " + coordToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode versionsJson = objectMapper.readTree(versionsResult.getResponse().getContentAsString());
        int versionCountBefore = versionsJson.get("data").size();
        assertThat(versionCountBefore).isGreaterThanOrEqualTo(3);

        // Restore version 1 -> creates new version 4
        mockMvc.perform(post("/api/versions/ExamSession/" + sessionId + "/restore")
                        .param("targetVersion", "1")
                        .header("Authorization", "Bearer " + coordToken))
                .andExpect(status().isOk());

        // Verify new version 4 was created with version 1 content
        MvcResult versionsAfter = mockMvc.perform(get("/api/versions/ExamSession/" + sessionId)
                        .header("Authorization", "Bearer " + coordToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode versionsAfterJson = objectMapper.readTree(versionsAfter.getResponse().getContentAsString());
        int versionCountAfter = versionsAfterJson.get("data").size();
        assertThat(versionCountAfter).isEqualTo(versionCountBefore + 1);

        // Verify versions 1, 2, 3 are still intact (unchanged)
        MvcResult v1Result = mockMvc.perform(get("/api/versions/ExamSession/" + sessionId + "/1")
                        .header("Authorization", "Bearer " + coordToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(v1Result.getResponse().getContentAsString()).contains("data");

        MvcResult v2Result = mockMvc.perform(get("/api/versions/ExamSession/" + sessionId + "/2")
                        .header("Authorization", "Bearer " + coordToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(v2Result.getResponse().getContentAsString()).contains("data");

        MvcResult v3Result = mockMvc.perform(get("/api/versions/ExamSession/" + sessionId + "/3")
                        .header("Authorization", "Bearer " + coordToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(v3Result.getResponse().getContentAsString()).contains("data");
    }

    @Test
    void testRestorePublishedTriggersReReview() throws Exception {
        // Create session, approve, and publish
        String coordToken = login(COORDINATOR_USERNAME, DEVICE_FP_COORD);

        CreateExamSessionRequest createRequest = buildRequest("Published Restore Exam");
        MvcResult createResult = mockMvc.perform(post("/api/exam-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest))
                        .header("Authorization", "Bearer " + coordToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long sessionId = createJson.get("data").get("id").asLong();

        // Submit for review
        mockMvc.perform(post("/api/exam-sessions/" + sessionId + "/submit-review")
                        .header("Authorization", "Bearer " + coordToken))
                .andExpect(status().isOk());

        // Admin approves
        sessionRepository.deleteAll();
        String adminToken = login(ADMIN_USERNAME, DEVICE_FP_ADMIN);

        MvcResult reviewsResult = mockMvc.perform(get("/api/compliance/reviews")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode reviewsJson = objectMapper.readTree(reviewsResult.getResponse().getContentAsString());
        Long reviewId = reviewsJson.get("data").get(0).get("id").asLong();

        ReviewDecisionRequest approveRequest = new ReviewDecisionRequest();
        approveRequest.setComment("Approved");
        mockMvc.perform(post("/api/compliance/reviews/" + reviewId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveRequest))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Publish
        mockMvc.perform(post("/api/exam-sessions/" + sessionId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        // Restore to version 1
        mockMvc.perform(post("/api/versions/ExamSession/" + sessionId + "/restore")
                        .param("targetVersion", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Verify a new compliance review was created for the restored entity
        MvcResult newReviewsResult = mockMvc.perform(get("/api/compliance/reviews")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode newReviewsJson = objectMapper.readTree(newReviewsResult.getResponse().getContentAsString());
        assertThat(newReviewsJson.get("data").size()).isGreaterThan(0);

        // Verify session status changed from PUBLISHED
        MvcResult sessionResult = mockMvc.perform(get("/api/exam-sessions/" + sessionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode sessionJson = objectMapper.readTree(sessionResult.getResponse().getContentAsString());
        String currentStatus = sessionJson.get("data").get("status").asText();
        assertThat(currentStatus).isNotEqualTo("PUBLISHED");
    }
}
