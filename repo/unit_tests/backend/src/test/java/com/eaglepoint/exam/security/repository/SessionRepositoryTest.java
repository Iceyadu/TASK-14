package com.eaglepoint.exam.security.repository;

import com.eaglepoint.exam.security.model.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
)
@ActiveProfiles({"test", "integration"})
class SessionRepositoryTest {

    @Autowired
    private SessionRepository sessionRepository;

    @Test
    @Transactional
    void testFindBySessionTokenAndDeleteByUserId() {
        Session session = new Session();
        session.setSessionToken("repo-test-token");
        session.setUserId(555L);
        session.setDeviceFingerprint("repo-fp");
        session.setSigningKey("repo-signing-key");
        session.setCreatedAt(LocalDateTime.now());
        session.setLastActiveAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        session.setRememberDevice(false);
        sessionRepository.save(session);

        assertThat(sessionRepository.findBySessionToken("repo-test-token")).isPresent();
        assertThat(sessionRepository.findByUserId(555L)).hasSize(1);

        sessionRepository.deleteByUserId(555L);
        assertThat(sessionRepository.findBySessionToken("repo-test-token")).isEmpty();
    }
}
