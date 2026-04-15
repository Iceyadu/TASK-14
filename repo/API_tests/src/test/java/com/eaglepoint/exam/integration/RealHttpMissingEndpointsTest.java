package com.eaglepoint.exam.integration;

import com.eaglepoint.exam.ExamSchedulingApplication;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.repository.SessionRepository;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.shared.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Socket-level endpoint coverage for previously missing exact paths.
 */
@SpringBootTest(
        classes = ExamSchedulingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
)
@ActiveProfiles({"test", "integration"})
class RealHttpMissingEndpointsTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionRepository sessionRepository;

    @LocalServerPort
    private int port;

    private static final String ADMIN_USER = "real_http_missing_admin";
    private static final String ADMIN_PASS = "Admin@12345678";
    private static final String STUDENT_USER = "real_http_missing_student";
    private static final String STUDENT_PASS = "Student@12345678";

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        seed(ADMIN_USER, ADMIN_PASS, Role.ADMIN);
        seed(STUDENT_USER, STUDENT_PASS, Role.STUDENT);
    }

    private void seed(String username, String password, Role role) {
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

    @SuppressWarnings("unchecked")
    private String login(String username, String password, String fp) {
        String url = "http://localhost:" + port + "/api/auth/login";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {
                  "username": "%s",
                  "password": "%s",
                  "deviceFingerprint": "%s"
                }
                """.formatted(username, password, fp);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        return String.valueOf(data.get("sessionToken"));
    }

    private HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void assertNotServerError(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().value()).isLessThan(500);
    }

    @Test
    void testPreviouslyMissingPathsOverRealHttp() {
        String adminToken = login(ADMIN_USER, ADMIN_PASS, "fp-rh-admin");
        String studentToken = login(STUDENT_USER, STUDENT_PASS, "fp-rh-student");
        String base = "http://localhost:" + port;

        assertNotServerError(restTemplate.exchange(base + "/api/auth/session", HttpMethod.GET,
                new HttpEntity<>(auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/auth/logout", HttpMethod.POST,
                new HttpEntity<>(auth(adminToken)), String.class));
        adminToken = login(ADMIN_USER, ADMIN_PASS, "fp-rh-admin-2");

        assertNotServerError(restTemplate.exchange(base + "/api/compliance/reviews/999999", HttpMethod.GET,
                new HttpEntity<>(auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/compliance/reviews/999999/reject", HttpMethod.POST,
                new HttpEntity<>("{\"comment\":\"x\",\"requiredChanges\":\"y\"}", auth(adminToken)), String.class));

        assertNotServerError(restTemplate.exchange(base + "/api/jobs/999999", HttpMethod.GET,
                new HttpEntity<>(auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/jobs/999999/cancel", HttpMethod.POST,
                new HttpEntity<>(auth(adminToken)), String.class));

        assertNotServerError(restTemplate.exchange(base + "/api/notifications/999999/cancel", HttpMethod.POST,
                new HttpEntity<>(auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/notifications/inbox/999999/read", HttpMethod.POST,
                new HttpEntity<>(auth(studentToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/notifications/subscriptions", HttpMethod.GET,
                new HttpEntity<>(auth(studentToken)), String.class));

        assertNotServerError(restTemplate.exchange(base + "/api/proctor-assignments?examSessionId=999999", HttpMethod.GET,
                new HttpEntity<>(auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/proctor-assignments/999999", HttpMethod.DELETE,
                new HttpEntity<>(auth(adminToken)), String.class));

        assertNotServerError(restTemplate.exchange(base + "/api/campuses/999999", HttpMethod.GET,
                new HttpEntity<>(auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/campuses/999999", HttpMethod.DELETE,
                new HttpEntity<>(auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/rooms/999999", HttpMethod.GET,
                new HttpEntity<>(auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/rooms/999999", HttpMethod.PUT,
                new HttpEntity<>("{\"name\":\"x\",\"campusId\":1}", auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/rooms/999999", HttpMethod.DELETE,
                new HttpEntity<>(auth(adminToken)), String.class));

        assertNotServerError(restTemplate.exchange(base + "/api/rosters/999999", HttpMethod.GET,
                new HttpEntity<>(auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/rosters", HttpMethod.POST,
                new HttpEntity<>("{\"studentUserId\":1,\"classId\":1,\"termId\":1}", auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/rosters/999999", HttpMethod.PUT,
                new HttpEntity<>("{\"studentUserId\":1,\"classId\":1,\"termId\":1}", auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/rosters/999999", HttpMethod.DELETE,
                new HttpEntity<>(auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/rosters/import/999999/errors", HttpMethod.GET,
                new HttpEntity<>(auth(adminToken)), String.class));

        assertNotServerError(restTemplate.exchange(base + "/api/users/999999", HttpMethod.GET,
                new HttpEntity<>(auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/users/999999", HttpMethod.PUT,
                new HttpEntity<>("{\"fullName\":\"x\"}", auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/users/999999/scope", HttpMethod.PUT,
                new HttpEntity<>("[{\"scopeType\":\"CAMPUS\",\"scopeId\":1}]", auth(adminToken)), String.class));

        assertNotServerError(restTemplate.exchange(base + "/api/exam-sessions/999999/unpublish", HttpMethod.POST,
                new HttpEntity<>(auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/exam-sessions/999999/archive", HttpMethod.POST,
                new HttpEntity<>(auth(adminToken)), String.class));
        assertNotServerError(restTemplate.exchange(base + "/api/versions/ExamSession/999999/compare?from=1&to=2",
                HttpMethod.GET, new HttpEntity<>(auth(adminToken)), String.class));
    }
}
