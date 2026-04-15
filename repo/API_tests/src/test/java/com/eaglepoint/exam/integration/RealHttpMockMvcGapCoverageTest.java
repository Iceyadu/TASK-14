package com.eaglepoint.exam.integration;

import com.eaglepoint.exam.ExamSchedulingApplication;
import com.eaglepoint.exam.anticheat.model.AntiCheatFlag;
import com.eaglepoint.exam.anticheat.repository.AntiCheatFlagRepository;
import com.eaglepoint.exam.auth.repository.ManagedDeviceRepository;
import com.eaglepoint.exam.jobs.model.JobRun;
import com.eaglepoint.exam.jobs.model.JobRunStatus;
import com.eaglepoint.exam.jobs.repository.JobRunRepository;
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
class RealHttpMockMvcGapCoverageTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ManagedDeviceRepository managedDeviceRepository;
    @Autowired private AntiCheatFlagRepository antiCheatFlagRepository;
    @Autowired private JobRunRepository jobRunRepository;
    @Autowired private CampusRepository campusRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private TermRepository termRepository;

    @LocalServerPort
    private int port;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "Admin@12345678";
    private static final String STUDENT_USERNAME = "student.chen";
    private static final String STUDENT_PASSWORD = "Admin@12345678";

    private Long adminId;
    private Long studentId;
    private Long campusId;
    private Long gradeId;
    private Long classId;
    private Long courseId;
    private Long roomId;
    private Long termId;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        managedDeviceRepository.deleteAll();
        adminId = ensureUser(ADMIN_USERNAME, ADMIN_PASSWORD, Role.ADMIN);
        studentId = ensureUser(STUDENT_USERNAME, STUDENT_PASSWORD, Role.STUDENT);

        Campus campus = campusRepository.findAll().stream().findFirst().orElseGet(() -> {
            Campus c = new Campus();
            c.setName("Gap Campus");
            c.setAddress("Gap Address");
            return campusRepository.save(c);
        });
        campusId = campus.getId();

        Grade grade = gradeRepository.findAll().stream().findFirst().orElseGet(() -> {
            Grade g = new Grade();
            g.setName("Gap Grade");
            g.setLevel(7);
            return gradeRepository.save(g);
        });
        gradeId = grade.getId();

        SchoolClass schoolClass = classRepository.findAll().stream().findFirst().orElseGet(() -> {
            SchoolClass sc = new SchoolClass();
            sc.setName("Gap Class");
            sc.setCampusId(campusId);
            sc.setGradeId(gradeId);
            return classRepository.save(sc);
        });
        classId = schoolClass.getId();

        Course course = courseRepository.findAll().stream().findFirst().orElseGet(() -> {
            Course c = new Course();
            c.setName("Gap Course");
            c.setGradeId(gradeId);
            return courseRepository.save(c);
        });
        courseId = course.getId();

        Room room = roomRepository.findAll().stream().findFirst().orElseGet(() -> {
            Room r = new Room();
            r.setName("Gap Room");
            r.setCampusId(campusId);
            r.setCapacity(30);
            return roomRepository.save(r);
        });
        roomId = room.getId();

        Term term = termRepository.findAll().stream().findFirst().orElseGet(() -> {
            Term t = new Term();
            t.setName("Gap Term");
            t.setStartDate(LocalDate.now().minusDays(1));
            t.setEndDate(LocalDate.now().plusMonths(3));
            t.setIsActive(true);
            return termRepository.save(t);
        });
        termId = term.getId();
    }

    private Long ensureUser(String username, String password, Role role) {
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

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private String login(String username, String password, String fingerprint) throws Exception {
        String body = """
                {
                  "username":"%s",
                  "password":"%s",
                  "deviceFingerprint":"%s"
                }
                """.formatted(username, password, fingerprint);
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/api/auth/login",
                new HttpEntity<>(body, jsonHeaders()),
                String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return json(response).path("data").path("sessionToken").asText();
    }

    @Test
    void testRealHttpCoverageForPreviouslyMockMvcOnlyEndpoints() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD, "gap-admin-fp");
        String studentToken = login(STUDENT_USERNAME, STUDENT_PASSWORD, "gap-student-fp");

        // auth device endpoints
        String deviceFingerprint = "gap-device-" + System.currentTimeMillis();
        ResponseEntity<String> registerDevice = restTemplate.exchange(
                baseUrl() + "/api/auth/devices",
                HttpMethod.POST,
                new HttpEntity<>(
                        """
                        {"deviceFingerprint":"%s","description":"Gap device"}
                        """.formatted(deviceFingerprint),
                        authHeaders(adminToken)),
                String.class);
        assertThat(registerDevice.getStatusCode().is2xxSuccessful()).isTrue();
        Long deviceId = json(registerDevice).path("data").path("id").asLong();
        assertThat(deviceId).isPositive();

        ResponseEntity<String> listDevices = restTemplate.exchange(
                baseUrl() + "/api/auth/devices",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(json(listDevices).path("data").isArray()).isTrue();

        ResponseEntity<String> deleteDevice = restTemplate.exchange(
                baseUrl() + "/api/auth/devices/" + deviceId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(deleteDevice.getStatusCode().is2xxSuccessful()).isTrue();

        // subscriptions update path
        ResponseEntity<String> updateSubscriptions = restTemplate.exchange(
                baseUrl() + "/api/notifications/subscriptions",
                HttpMethod.PUT,
                new HttpEntity<>(
                        """
                        {
                          "subscriptions": {"GENERAL": true, "CHECK_IN_REMINDER": false},
                          "dndStartTime": "22:00:00",
                          "dndEndTime": "06:00:00"
                        }
                        """,
                        authHeaders(studentToken)),
                String.class);
        assertThat(updateSubscriptions.getStatusCode().is2xxSuccessful()).isTrue();

        // admin auth actions
        ResponseEntity<String> terminateSessions = restTemplate.exchange(
                baseUrl() + "/api/auth/sessions/" + studentId + "/terminate",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(terminateSessions.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> unlockUser = restTemplate.exchange(
                baseUrl() + "/api/auth/users/" + studentId + "/unlock",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(unlockUser.getStatusCode().is2xxSuccessful()).isTrue();

        // anti-cheat review
        AntiCheatFlag flag = new AntiCheatFlag();
        flag.setStudentUserId(studentId);
        flag.setRuleType("MANUAL_TEST");
        flag.setStatus("PENDING");
        flag.setDetailsJson("{}");
        flag = antiCheatFlagRepository.save(flag);
        ResponseEntity<String> reviewFlag = restTemplate.exchange(
                baseUrl() + "/api/anticheat/flags/" + flag.getId() + "/review",
                HttpMethod.POST,
                new HttpEntity<>("{\"decision\":\"DISMISS\",\"comment\":\"reviewed\"}", authHeaders(adminToken)),
                String.class);
        assertThat(reviewFlag.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(json(reviewFlag).path("data").path("status").asText()).isEqualTo("DISMISSED");

        // campuses + rooms + reference endpoints
        ResponseEntity<String> listCampuses = restTemplate.exchange(
                baseUrl() + "/api/campuses",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(json(listCampuses).path("data").isArray()).isTrue();

        ResponseEntity<String> createCampus = restTemplate.exchange(
                baseUrl() + "/api/campuses",
                HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Gap Campus API\",\"address\":\"Addr\"}", authHeaders(adminToken)),
                String.class);
        Long createdCampusId = json(createCampus).path("data").path("id").asLong();
        assertThat(createdCampusId).isPositive();

        ResponseEntity<String> updateCampus = restTemplate.exchange(
                baseUrl() + "/api/campuses/" + createdCampusId,
                HttpMethod.PUT,
                new HttpEntity<>("{\"name\":\"Gap Campus API Updated\",\"address\":\"Addr2\"}", authHeaders(adminToken)),
                String.class);
        assertThat(json(updateCampus).path("data").path("name").asText()).contains("Updated");

        ResponseEntity<String> createRoom = restTemplate.exchange(
                baseUrl() + "/api/rooms",
                HttpMethod.POST,
                new HttpEntity<>(
                        """
                        {"campusId": %d, "name":"Gap API Room", "building":"A", "capacity":20, "facilities":"Projector"}
                        """.formatted(createdCampusId),
                        authHeaders(adminToken)),
                String.class);
        assertThat(createRoom.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> listRooms = restTemplate.exchange(
                baseUrl() + "/api/rooms?campusId=" + createdCampusId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(json(listRooms).path("data").isArray()).isTrue();

        for (String endpoint : new String[]{"/api/terms", "/api/grades", "/api/classes", "/api/courses"}) {
            ResponseEntity<String> ref = restTemplate.exchange(
                    baseUrl() + endpoint,
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders(adminToken)),
                    String.class);
            assertThat(ref.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(json(ref).path("data").isArray()).isTrue();
        }

        // users create + concurrent session toggle
        String newUsername = "gap_user_" + System.currentTimeMillis();
        ResponseEntity<String> createUser = restTemplate.exchange(
                baseUrl() + "/api/users",
                HttpMethod.POST,
                new HttpEntity<>(
                        """
                        {
                          "username":"%s",
                          "password":"Strong@123456",
                          "fullName":"Gap User",
                          "role":"STUDENT"
                        }
                        """.formatted(newUsername),
                        authHeaders(adminToken)),
                String.class);
        Long newUserId = json(createUser).path("data").path("id").asLong();
        assertThat(newUserId).isPositive();

        ResponseEntity<String> toggleConcurrent = restTemplate.exchange(
                baseUrl() + "/api/users/" + newUserId + "/concurrent-sessions",
                HttpMethod.PUT,
                new HttpEntity<>("{\"allowed\":false}", authHeaders(adminToken)),
                String.class);
        assertThat(json(toggleConcurrent).path("data").path("allowConcurrentSessions").asBoolean()).isFalse();

        // exam create + get by id
        String examBody = """
                {
                  "name":"Gap Session %d",
                  "termId":%d,
                  "courseId":%d,
                  "campusId":%d,
                  "examDate":"%s",
                  "startTime":"09:00:00",
                  "endTime":"11:00:00",
                  "roomId":%d,
                  "classIds":[%d]
                }
                """.formatted(System.currentTimeMillis(), termId, courseId, campusId, LocalDate.now().plusDays(15), roomId, classId);
        ResponseEntity<String> createSession = restTemplate.exchange(
                baseUrl() + "/api/exam-sessions",
                HttpMethod.POST,
                new HttpEntity<>(examBody, authHeaders(adminToken)),
                String.class);
        Long examSessionId = json(createSession).path("data").path("id").asLong();
        assertThat(examSessionId).isPositive();

        ResponseEntity<String> getSession = restTemplate.exchange(
                baseUrl() + "/api/exam-sessions/" + examSessionId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(json(getSession).path("data").path("id").asLong()).isEqualTo(examSessionId);

        // proctor assignment create
        ResponseEntity<String> createProctor = restTemplate.exchange(
                baseUrl() + "/api/proctor-assignments",
                HttpMethod.POST,
                new HttpEntity<>(
                        """
                        {"userId": %d, "examSessionId": %d, "roomId": %d}
                        """.formatted(adminId, examSessionId, roomId),
                        authHeaders(adminToken)),
                String.class);
        assertThat(json(createProctor).path("data").path("examSessionId").asLong()).isEqualTo(examSessionId);

        // jobs rerun
        JobRun failedJob = new JobRun();
        failedJob.setJobType("NOTIFICATION_SEND");
        failedJob.setEntityId(1L);
        failedJob.setShardKey(0);
        failedJob.setStatus(JobRunStatus.FAILED);
        failedJob.setAttempts(3);
        failedJob.setMaxAttempts(3);
        failedJob.setCreatedBy(adminId);
        failedJob = jobRunRepository.save(failedJob);

        ResponseEntity<String> rerunJob = restTemplate.exchange(
                baseUrl() + "/api/jobs/" + failedJob.getId() + "/rerun",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(rerunJob.getStatusCode().is2xxSuccessful()).isTrue();
        Long rerunId = json(rerunJob).path("data").path("id").asLong();
        assertThat(rerunId).isPositive();
        assertThat(jobRunRepository.findById(failedJob.getId()).orElseThrow().getStatus())
                .isEqualTo(JobRunStatus.MANUALLY_RERUN);

        // roster create + get + update + export
        ResponseEntity<String> createRoster = restTemplate.exchange(
                baseUrl() + "/api/rosters",
                HttpMethod.POST,
                new HttpEntity<>(
                        """
                        {
                          "studentUserId": %d,
                          "classId": %d,
                          "termId": %d,
                          "studentIdNumber":"GAP-R1",
                          "guardianContact":"guardian@test.local",
                          "accommodationNotes":"none"
                        }
                        """.formatted(studentId, classId, termId),
                        authHeaders(adminToken)),
                String.class);
        Long rosterId = json(createRoster).path("data").path("id").asLong();
        assertThat(rosterId).isPositive();

        ResponseEntity<String> getRoster = restTemplate.exchange(
                baseUrl() + "/api/rosters/" + rosterId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(json(getRoster).path("data").path("id").asLong()).isEqualTo(rosterId);

        ResponseEntity<String> updateRoster = restTemplate.exchange(
                baseUrl() + "/api/rosters/" + rosterId,
                HttpMethod.PUT,
                new HttpEntity<>(
                        """
                        {
                          "studentUserId": %d,
                          "classId": %d,
                          "termId": %d,
                          "studentIdNumber":"GAP-R2",
                          "guardianContact":"guardian2@test.local",
                          "accommodationNotes":"updated"
                        }
                        """.formatted(studentId, classId, termId),
                        authHeaders(adminToken)),
                String.class);
        assertThat(json(updateRoster).path("data").path("id").asLong()).isEqualTo(rosterId);

        ResponseEntity<String> exportRoster = restTemplate.exchange(
                baseUrl() + "/api/rosters/export?classId=" + classId + "&termId=" + termId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(exportRoster.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(exportRoster.getBody()).contains("student_username");

        // import rollback via real HTTP
        String csv = """
                student_username,class_name,term_name,student_id_number,guardian_contact,accommodation_notes
                %s,%s,%s,ROLL-1,guardian-roll@example.com,none
                """.formatted(STUDENT_USERNAME, classRepository.findById(classId).orElseThrow().getName(),
                termRepository.findById(termId).orElseThrow().getName());
        ByteArrayResource resource = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "gap-rollback.csv";
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
        Long importJobId = json(upload).path("data").path("jobId").asLong();
        assertThat(importJobId).isPositive();

        ResponseEntity<String> commit = restTemplate.exchange(
                baseUrl() + "/api/rosters/import/" + importJobId + "/commit",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(commit.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> rollback = restTemplate.exchange(
                baseUrl() + "/api/rosters/import/" + importJobId + "/rollback",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(rollback.getStatusCode().is2xxSuccessful()).isTrue();

        // versions/{entityType}/{entityId}/{versionNumber}
        ResponseEntity<String> versionOne = restTemplate.exchange(
                baseUrl() + "/api/versions/ExamSession/" + examSessionId + "/1",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                String.class);
        assertThat(versionOne.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(json(versionOne).path("data").path("versionNumber").asInt()).isEqualTo(1);
    }
}
