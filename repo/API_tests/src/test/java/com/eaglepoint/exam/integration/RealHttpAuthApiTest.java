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
 * Socket-level API test (no MockMvc) for auth/session endpoints.
 */
@SpringBootTest(
        classes = ExamSchedulingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
)
@ActiveProfiles({"test", "integration"})
class RealHttpAuthApiTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionRepository sessionRepository;

    @LocalServerPort
    private int port;

    private static final String USERNAME = "real_http_admin_test";
    private static final String PASSWORD = "Admin@12345678";

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        userRepository.findByUsername(USERNAME).orElseGet(() -> {
            User user = new User();
            user.setUsername(USERNAME);
            user.setPasswordHash(new BCryptPasswordEncoder(4).encode(PASSWORD));
            user.setFullName("Real HTTP Admin");
            user.setRole(Role.ADMIN);
            user.setAllowConcurrentSessions(true);
            user.setFailedLoginAttempts(0);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(user);
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void testRealHttpLoginSessionAndLogout() {
        String base = "http://localhost:" + port;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String loginBody = """
                {
                  "username": "%s",
                  "password": "%s",
                  "deviceFingerprint": "real-http-device-1"
                }
                """.formatted(USERNAME, PASSWORD);
        ResponseEntity<Map> login = restTemplate.postForEntity(
                base + "/api/auth/login",
                new HttpEntity<>(loginBody, headers),
                Map.class);
        assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> loginData = (Map<String, Object>) login.getBody().get("data");
        String token = String.valueOf(loginData.get("sessionToken"));
        assertThat(token).isNotBlank();

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);
        ResponseEntity<Map> session = restTemplate.exchange(
                base + "/api/auth/session",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                Map.class);
        assertThat(session.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<Map> logout = restTemplate.exchange(
                base + "/api/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders),
                Map.class);
        assertThat(logout.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
