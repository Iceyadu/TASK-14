package com.eaglepoint.exam.integration;

import com.eaglepoint.exam.ExamSchedulingApplication;
import com.eaglepoint.exam.jobs.service.JobService;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = ExamSchedulingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
)
@ActiveProfiles({"test", "integration"})
class RealHttpWorkflowCoverageTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CampusRepository campusRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private TermRepository termRepository;
    @Autowired private JobService jobService;

    @LocalServerPort
    private int port;

    private static final String ADMIN_USERNAME = "real_http_workflow_admin";
    private static final String STUDENT_USERNAME = "real_http_workflow_student";
    private static final String ADMIN_PASSWORD = "Admin@12345678";
    private static final String STUDENT_PASSWORD = "Student@12345678";

    private Long studentId;
    private Long campusId;
    private Long gradeId;
    private Long courseId;
    private Long roomId;
    private Long classId;
    private Long termId;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        studentId = seedUser(STUDENT_USERNAME, STUDENT_PASSWORD, Role.STUDENT);
        seedUser(ADMIN_USERNAME, ADMIN_PASSWORD, Role.ADMIN);

        Campus campus = campusRepository.findAll().stream().findFirst().orElseGet(() -> {
            Campus c = new Campus();
            c.setName("Real HTTP Campus");
            c.setAddress("coverage lane");
            return campusRepository.save(c);
        });
        campusId = campus.getId();

        Grade grade = gradeRepository.findAll().stream().findFirst().orElseGet(() -> {
            Grade g = new Grade();
            g.setName("Grade 8");
            g.setLevel(8);
            return gradeRepository.save(g);
        });
        gradeId = grade.getId();

        Course course = courseRepository.findAll().stream().findFirst().orElseGet(() -> {
            Course c = new Course();
            c.setName("Physics");
            c.setGradeId(gradeId);
            return courseRepository.save(c);
        });
        courseId = course.getId();

        Room room = roomRepository.findAll().stream().findFirst().orElseGet(() -> {
            Room r = new Room();
            r.setName("RH-101");
            r.setCampusId(campusId);
            r.setCapacity(30);
            return roomRepository.save(r);
        });
        roomId = room.getId();

        SchoolClass schoolClass = classRepository.findAll().stream().findFirst().orElseGet(() -> {
            SchoolClass sc = new SchoolClass();
            sc.setName("RH Class A");
            sc.setCampusId(campusId);
            sc.setGradeId(gradeId);
            return classRepository.save(sc);
        });
        classId = schoolClass.getId();

        Term term = termRepository.findAll().stream().findFirst().orElseGet(() -> {
            Term t = new Term();
            t.setName("RH Term");
            t.setStartDate(LocalDate.now().minusDays(1));
            t.setEndDate(LocalDate.now().plusMonths(4));
            t.setIsActive(true);
            return termRepository.save(t);
        });
        termId = term.getId();
    }

    private Long seedUser(String username, String password, Role role) {
        return userRepository.findByUsername(username).map(User::getId).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setPasswordHash(new BCryptPasswordEncoder(4).encode(password));
            user.setFullName(username);
            user.setRole(role);
            user.setAllowConcurrentSessions(true);
            user.setFailedLoginAttempts(0);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(user).getId();
        });
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private JsonNode toJson(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String login(String username, String password, String fp) throws Exception {
        String body = """
                {
                  "username": "%s",
                  "password": "%s",
                  "deviceFingerprint": "%s"
                }
                """.formatted(username, password, fp);
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(body, jsonHeaders()),
                String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return toJson(response).path("data").path("sessionToken").asText();
    }

    private Long findPendingReviewId(String token, String entityType, Long entityId) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/api/compliance/reviews",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                String.class);
        JsonNode data = toJson(response).path("data");
        for (JsonNode review : data) {
            if (entityType.equals(review.path("entityType").asText())
                    && entityId.equals(review.path("entityId").asLong())) {
                return review.path("id").asLong();
            }
        }
        return null;
    }

    @Test
    void testRealHttpHighValueWorkflowsHaveDeepAssertions() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD, "rh-admin-workflow");
        String studentToken = login(STUDENT_USERNAME, STUDENT_PASSWORD, "rh-student-workflow");

        // create roster entry for schedule visibility
        String rosterBody = """
                {
                  "studentUserId": %d,
                  "classId": %d,
                  "termId": %d,
                  "studentIdNumber": "RH-SID",
                  "guardianContact": "guardian@example.local",
                  "accommodationNotes": "none"
                }
                """.formatted(studentId, classId, termId);
        ResponseEntity<String> rosterCreate = restTemplate.exchange(
                baseUrl() + "/api/rosters",
                HttpMethod.POST,
                new HttpEntity<>(rosterBody, authHeaders(adminToken)),
                String.class);
        assertThat(rosterCreate.getStatusCode().is2xxSuccessful()).isTrue();
        Long rosterId = toJson(rosterCreate).path("data").path("id").asLong();
        assertThat(rosterId).isPositive();

        // exam session lifecycle: create -> submit -> approve -> publish -> unpublish -> archive
        String sessionName = "RH Session " + System.currentTimeMillis();
        String sessionBody = """
                {
                  "name": "%s",
                  "termId": %d,
                  "courseId": %d,
                  "campusId": %d,
                  "examDate": "%s",
                  "startTime": "09:00:00",
                  "endTime": "11:00:00",
                  "roomId": %d,
                  "classIds": [%d]
                }
                """.formatted(sessionName, termId, courseId, campusId, LocalDate.now().plusDays(25), roomId, classId);
        ResponseEntity<String> createSession = restTemplate.exchange(
                baseUrl() + "/api/exam-sessions",
                HttpMethod.POST,
                new HttpEntity<>(sessionBody, authHeaders(adminToken)),
                String.class);
        JsonNode createdSessionJson = toJson(createSession);
        Long sessionId = createdSessionJson.path("data").path("id").asLong();
        assertThat(createdSessionJson.path("data").path("status").asText()).isEqualTo("DRAFT");

        ResponseEntity<String> submitSession = restTemplate.exchange(
                baseUrl() + "/api/exam-sessions/" + sessionId + "/submit-review",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(toJson(submitSession).path("data").path("status").asText())
                .isEqualTo("SUBMITTED_FOR_COMPLIANCE_REVIEW");

        Long examReviewId = findPendingReviewId(adminToken, "ExamSession", sessionId);
        assertThat(examReviewId).isNotNull();
        ResponseEntity<String> approveExam = restTemplate.exchange(
                baseUrl() + "/api/compliance/reviews/" + examReviewId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>("{\"comment\":\"approved\"}", authHeaders(adminToken)),
                String.class);
        assertThat(approveExam.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> publishSession = restTemplate.exchange(
                baseUrl() + "/api/exam-sessions/" + sessionId + "/publish",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(toJson(publishSession).path("data").path("status").asText()).isEqualTo("PUBLISHED");

        ResponseEntity<String> studentSchedule = restTemplate.exchange(
                baseUrl() + "/api/exam-sessions/student/schedule",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(studentToken)),
                String.class);
        assertThat(toJson(studentSchedule).path("data").isArray()).isTrue();

        ResponseEntity<String> unpublish = restTemplate.exchange(
                baseUrl() + "/api/exam-sessions/" + sessionId + "/unpublish",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(toJson(unpublish).path("data").path("status").asText()).isEqualTo("UNPUBLISHED");

        ResponseEntity<String> archive = restTemplate.exchange(
                baseUrl() + "/api/exam-sessions/" + sessionId + "/archive",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(toJson(archive).path("data").path("status").asText()).isEqualTo("ARCHIVED");

        // create second draft session for version update/compare/restore
        String versionSessionBody = sessionBody.replace(sessionName, sessionName + "-V");
        ResponseEntity<String> versionCreate = restTemplate.exchange(
                baseUrl() + "/api/exam-sessions",
                HttpMethod.POST,
                new HttpEntity<>(versionSessionBody, authHeaders(adminToken)),
                String.class);
        Long versionSessionId = toJson(versionCreate).path("data").path("id").asLong();

        String updateBody = versionSessionBody.replace("-V", "-V-Updated");
        ResponseEntity<String> updateSession = restTemplate.exchange(
                baseUrl() + "/api/exam-sessions/" + versionSessionId,
                HttpMethod.PUT,
                new HttpEntity<>(updateBody, authHeaders(adminToken)),
                String.class);
        assertThat(updateSession.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> versions = restTemplate.exchange(
                baseUrl() + "/api/versions/ExamSession/" + versionSessionId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        JsonNode versionsData = toJson(versions).path("data");
        assertThat(versionsData.isArray()).isTrue();
        assertThat(versionsData.size()).isGreaterThanOrEqualTo(2);

        ResponseEntity<String> compare = restTemplate.exchange(
                baseUrl() + "/api/versions/ExamSession/" + versionSessionId + "/compare?from=1&to=2",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        JsonNode compareData = toJson(compare).path("data");
        assertThat(compareData.has("from")).isTrue();
        assertThat(compareData.has("to")).isTrue();

        ResponseEntity<String> restore = restTemplate.exchange(
                baseUrl() + "/api/versions/ExamSession/" + versionSessionId + "/restore?targetVersion=1",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(toJson(restore).path("data").path("versionNumber").asInt()).isGreaterThanOrEqualTo(3);

        // notification publish with inbox/delivery assertions
        String notificationTitle = "RH Notification " + System.currentTimeMillis();
        String notificationBody = """
                {
                  "title": "%s",
                  "content": "deliver this notification",
                  "eventType": "GENERAL",
                  "targetType": "INDIVIDUAL",
                  "targetIds": [%d]
                }
                """.formatted(notificationTitle, studentId);
        ResponseEntity<String> createNotification = restTemplate.exchange(
                baseUrl() + "/api/notifications",
                HttpMethod.POST,
                new HttpEntity<>(notificationBody, authHeaders(adminToken)),
                String.class);
        Long notificationId = toJson(createNotification).path("data").path("id").asLong();
        assertThat(toJson(createNotification).path("data").path("status").asText()).isEqualTo("DRAFT");

        ResponseEntity<String> submitNotification = restTemplate.exchange(
                baseUrl() + "/api/notifications/" + notificationId + "/submit-review",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(toJson(submitNotification).path("data").path("status").asText())
                .isEqualTo("DRAFT");

        Long notifReviewId = findPendingReviewId(adminToken, "Notification", notificationId);
        assertThat(notifReviewId).isNotNull();
        ResponseEntity<String> approveNotif = restTemplate.exchange(
                baseUrl() + "/api/compliance/reviews/" + notifReviewId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>("{\"comment\":\"notification approved\"}", authHeaders(adminToken)),
                String.class);
        assertThat(approveNotif.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> publishNotif = restTemplate.exchange(
                baseUrl() + "/api/notifications/" + notificationId + "/publish",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(toJson(publishNotif).path("data").path("status").asText()).isIn("QUEUED", "FALLBACK_TO_IN_APP", "DELIVERED");
        jobService.processNextJob();
        jobService.processNextJob();

        ResponseEntity<String> deliveryStatus = restTemplate.exchange(
                baseUrl() + "/api/notifications/delivery-status?notificationId=" + notificationId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(toJson(deliveryStatus).path("data").isArray()).isTrue();
        assertThat(toJson(deliveryStatus).path("data").size()).isGreaterThan(0);

        ResponseEntity<String> inbox = restTemplate.exchange(
                baseUrl() + "/api/notifications/inbox",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(studentToken)),
                String.class);
        JsonNode inboxData = toJson(inbox).path("data");
        assertThat(inboxData.isArray()).isTrue();
        assertThat(inboxData.toString()).contains(notificationTitle);
        Long inboxId = inboxData.get(0).path("id").asLong();
        ResponseEntity<String> markRead = restTemplate.exchange(
                baseUrl() + "/api/notifications/inbox/" + inboxId + "/read",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(studentToken)),
                String.class);
        assertThat(markRead.getStatusCode().is2xxSuccessful()).isTrue();

        // real HTTP roster import upload -> commit -> errors
        String csv = """
                student_username,class_name,term_name,student_id_number,guardian_contact,accommodation_notes
                %s,%s,%s,RH-ID,parent@example.com,none
                """.formatted(STUDENT_USERNAME, classRepository.findById(classId).orElseThrow().getName(),
                termRepository.findById(termId).orElseThrow().getName());
        ByteArrayResource resource = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "real-http-import.csv";
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType("text/csv"));
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, fileHeaders);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", filePart);
        HttpHeaders multipartHeaders = new HttpHeaders();
        multipartHeaders.setBearerAuth(adminToken);
        multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> upload = restTemplate.exchange(
                baseUrl() + "/api/rosters/import/upload?entityType=RosterEntry",
                HttpMethod.POST,
                new HttpEntity<>(parts, multipartHeaders),
                String.class);
        JsonNode uploadJson = toJson(upload);
        Long jobId = uploadJson.path("data").path("jobId").asLong();
        assertThat(jobId).isPositive();
        assertThat(uploadJson.path("data").path("validRows").isArray()).isTrue();

        ResponseEntity<String> commit = restTemplate.exchange(
                baseUrl() + "/api/rosters/import/" + jobId + "/commit",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(commit.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> errors = restTemplate.exchange(
                baseUrl() + "/api/rosters/import/" + jobId + "/errors",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(errors.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void testRealHttpListEndpointsAssertPayloadShape() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD, "rh-admin-lists");
        String[] endpoints = {
                "/api/users",
                "/api/jobs",
                "/api/audit",
                "/api/anticheat/flags",
                "/api/notifications",
                "/api/exam-sessions",
                "/api/rosters",
                "/api/compliance/reviews"
        };

        for (String endpoint : endpoints) {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl() + endpoint,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(adminToken)),
                    String.class);
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            JsonNode json = toJson(response);
            assertThat(json.path("status").asText()).isEqualTo("success");
            assertThat(json.path("data").isArray()).isTrue();
            assertThat(json.has("pagination")).isTrue();
        }
    }
}
