package com.eaglepoint.exam.integration;

import com.eaglepoint.exam.ExamSchedulingApplication;
import com.eaglepoint.exam.auth.dto.LoginRequest;
import com.eaglepoint.exam.rooms.model.Campus;
import com.eaglepoint.exam.rooms.model.Grade;
import com.eaglepoint.exam.rooms.model.Room;
import com.eaglepoint.exam.rooms.model.SchoolClass;
import com.eaglepoint.exam.rooms.repository.CampusRepository;
import com.eaglepoint.exam.rooms.repository.ClassRepository;
import com.eaglepoint.exam.rooms.repository.GradeRepository;
import com.eaglepoint.exam.rooms.repository.RoomRepository;
import com.eaglepoint.exam.scheduling.model.Term;
import com.eaglepoint.exam.scheduling.repository.TermRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ExamSchedulingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "integration"})
class MissingEndpointsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private CampusRepository campusRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private TermRepository termRepository;
    @Autowired private RoomRepository roomRepository;

    private static final String ADMIN_USERNAME = "admin_missing_endpoints";
    private static final String ADMIN_PASSWORD = "Admin@12345678";
    private static final String STUDENT_USERNAME = "student_missing_endpoints";
    private static final String STUDENT_PASSWORD = "Student@12345678";

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        seedUser(ADMIN_USERNAME, ADMIN_PASSWORD, Role.ADMIN);
        seedUser(STUDENT_USERNAME, STUDENT_PASSWORD, Role.STUDENT);
    }

    private void seedUser(String username, String password, Role role) {
        userRepository.findByUsername(username).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setPasswordHash(new BCryptPasswordEncoder(4).encode(password));
            user.setFullName(username);
            user.setRole(role);
            user.setAllowConcurrentSessions(true);
            user.setFailedLoginAttempts(0);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(user);
        });
    }

    private String login(String username, String password, String fingerprint) throws Exception {
        LoginRequest req = new LoginRequest(username, password, fingerprint);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("data").get("sessionToken").asText();
    }

    @Test
    void testMissingPathsNowHaveDirectCoverage() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD, "fp-admin-missing");
        String studentToken = login(STUDENT_USERNAME, STUDENT_PASSWORD, "fp-student-missing");
        Long adminId = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow().getId();
        Long studentId = userRepository.findByUsername(STUDENT_USERNAME).orElseThrow().getId();

        // auth session + logout
        mockMvc.perform(get("/api/auth/session").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD, "fp-admin-missing-2");

        // campuses + rooms full path coverage (get by id / put / delete)
        MvcResult campusRes = mockMvc.perform(post("/api/campuses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing Endpoint Campus\",\"address\":\"Addr\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Long campusId = objectMapper.readTree(campusRes.getResponse().getContentAsString()).get("data").get("id").asLong();
        mockMvc.perform(get("/api/campuses/" + campusId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        MvcResult roomRes = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"R1\",\"campusId\":" + campusId + ",\"capacity\":20}"))
                .andExpect(status().isOk())
                .andReturn();
        Long roomId = objectMapper.readTree(roomRes.getResponse().getContentAsString()).get("data").get("id").asLong();
        mockMvc.perform(get("/api/rooms/" + roomId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/rooms/" + roomId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"R1-updated\",\"campusId\":" + campusId + ",\"capacity\":21}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/rooms/" + roomId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/campuses/" + campusId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // users get/update/scope
        mockMvc.perform(get("/api/users/" + studentId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/users/" + studentId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Updated Student Name\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/users/" + studentId + "/scope")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"scopeType\":\"CAMPUS\",\"scopeId\":1}]"))
                .andExpect(status().isOk());

        // roster CRUD + import errors endpoint
        Campus c = new Campus();
        c.setName("Roster Campus");
        c = campusRepository.save(c);
        Grade g = new Grade();
        g.setName("Roster Grade");
        g.setLevel(6);
        g = gradeRepository.save(g);
        SchoolClass sc = new SchoolClass();
        sc.setName("Roster Class");
        sc.setCampusId(c.getId());
        sc.setGradeId(g.getId());
        sc = classRepository.save(sc);
        Term t = new Term();
        t.setName("Roster Term");
        t.setStartDate(LocalDate.now().minusDays(1));
        t.setEndDate(LocalDate.now().plusDays(30));
        t.setIsActive(true);
        t = termRepository.save(t);

        MvcResult rosterCreate = mockMvc.perform(post("/api/rosters")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentUserId": %d,
                                  "classId": %d,
                                  "termId": %d,
                                  "studentIdNumber": "SID-MISSING",
                                  "guardianContact": "guardian@example.local",
                                  "accommodationNotes": "none"
                                }
                                """.formatted(studentId, sc.getId(), t.getId())))
                .andExpect(status().isOk())
                .andReturn();
        Long rosterId = objectMapper.readTree(rosterCreate.getResponse().getContentAsString()).get("data").get("id").asLong();
        mockMvc.perform(get("/api/rosters/" + rosterId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/rosters/" + rosterId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentUserId": %d,
                                  "classId": %d,
                                  "termId": %d,
                                  "studentIdNumber": "SID-MISSING-2",
                                  "guardianContact": "guardian2@example.local",
                                  "accommodationNotes": "updated"
                                }
                                """.formatted(studentId, sc.getId(), t.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/rosters/" + rosterId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/rosters/import/999999/errors").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // compliance get-by-id + reject
        MvcResult createNotif = mockMvc.perform(post("/api/notifications")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Reject Flow Notification",
                                  "content": "Review reject flow",
                                  "eventType": "GENERAL",
                                  "targetType": "INDIVIDUAL",
                                  "targetIds": [%d]
                                }
                                """.formatted(studentId)))
                .andExpect(status().isOk())
                .andReturn();
        Long notifId = objectMapper.readTree(createNotif.getResponse().getContentAsString()).get("data").get("id").asLong();
        mockMvc.perform(post("/api/notifications/" + notifId + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        MvcResult reviews = mockMvc.perform(get("/api/compliance/reviews")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Long reviewId = objectMapper.readTree(reviews.getResponse().getContentAsString()).get("data").get(0).get("id").asLong();
        mockMvc.perform(get("/api/compliance/reviews/" + reviewId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/compliance/reviews/" + reviewId + "/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"rejected for endpoint coverage\",\"requiredChanges\":\"needs edits\"}"))
                .andExpect(status().isOk());

        // notification cancel + subscription read + inbox read
        MvcResult cancelNotifCreate = mockMvc.perform(post("/api/notifications")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Cancelable Notification",
                                  "content": "cancel me",
                                  "eventType": "GENERAL",
                                  "targetType": "INDIVIDUAL",
                                  "targetIds": [%d]
                                }
                                """.formatted(studentId)))
                .andExpect(status().isOk())
                .andReturn();
        Long cancelNotifId = objectMapper.readTree(cancelNotifCreate.getResponse().getContentAsString()).get("data").get("id").asLong();
        mockMvc.perform(post("/api/notifications/" + cancelNotifId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications/subscriptions")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/notifications/inbox/999999/read")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound());

        // jobs endpoints missing paths
        mockMvc.perform(get("/api/jobs/999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/jobs/999999/cancel").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // proctor missing GET/DELETE exact path coverage
        mockMvc.perform(get("/api/proctor-assignments")
                        .param("examSessionId", "999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/proctor-assignments/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        // exam unpublish/archive + versions compare exact paths
        mockMvc.perform(post("/api/exam-sessions/999999/unpublish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/exam-sessions/999999/archive")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/versions/ExamSession/999999/compare")
                        .param("from", "1")
                        .param("to", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
