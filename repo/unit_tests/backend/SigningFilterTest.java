package com.eaglepoint.exam.security.filter;

import com.eaglepoint.exam.security.model.Session;
import com.eaglepoint.exam.security.repository.NonceReplayRepository;
import com.eaglepoint.exam.security.repository.SessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RequestSigningFilter} covering signature validation,
 * timestamp staleness, nonce replay, missing headers, and login bypass.
 */
@ExtendWith(MockitoExtension.class)
class SigningFilterTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private NonceReplayRepository nonceReplayRepository;

    @Mock
    private FilterChain filterChain;

    private RequestSigningFilter filter;
    private ObjectMapper objectMapper;

    private static final String SIGNING_KEY = "test-signing-key-base64-encoded";
    private static final String SESSION_TOKEN = "valid-session-token";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new RequestSigningFilter(sessionRepository, nonceReplayRepository, objectMapper);
        ReflectionTestUtils.setField(filter, "requireRequestSigning", true);
    }

    private String computeHmacSignature(String method, String path, String timestamp, String nonce, String body) throws Exception {
        String bodyHash = sha256Hex(body.getBytes(StandardCharsets.UTF_8));
        String signingString = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + bodyHash;
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(SIGNING_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(signingString.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hmacBytes.length * 2);
        for (byte b : hmacBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private Session createSession() {
        Session session = new Session();
        session.setSessionToken(SESSION_TOKEN);
        session.setSigningKey(SIGNING_KEY);
        session.setUserId(1L);
        return session;
    }

    @Test
    void testValidSignature() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String body = "";
        String signature = computeHmacSignature("GET", "/api/exam-sessions", timestamp, nonce, body);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/exam-sessions");
        request.setServletPath("/api/exam-sessions");
        request.addHeader("X-Timestamp", timestamp);
        request.addHeader("X-Nonce", nonce);
        request.addHeader("X-Signature", signature);
        request.addHeader("Authorization", "Bearer " + SESSION_TOKEN);

        MockHttpServletResponse response = new MockHttpServletResponse();

        when(sessionRepository.findBySessionToken(SESSION_TOKEN)).thenReturn(Optional.of(createSession()));
        when(nonceReplayRepository.existsById(nonce)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(any(), eq(response));
        verify(nonceReplayRepository).save(any());
    }

    @Test
    void testInvalidSignature() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/exam-sessions");
        request.setServletPath("/api/exam-sessions");
        request.addHeader("X-Timestamp", timestamp);
        request.addHeader("X-Nonce", nonce);
        request.addHeader("X-Signature", "tampered-invalid-signature");
        request.addHeader("Authorization", "Bearer " + SESSION_TOKEN);

        MockHttpServletResponse response = new MockHttpServletResponse();

        when(sessionRepository.findBySessionToken(SESSION_TOKEN)).thenReturn(Optional.of(createSession()));
        when(nonceReplayRepository.existsById(nonce)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void testStaleTimestamp() throws Exception {
        // Timestamp 200 seconds in the past (exceeds 120s window)
        String staleTimestamp = String.valueOf(Instant.now().getEpochSecond() - 200);
        String nonce = UUID.randomUUID().toString();
        String signature = computeHmacSignature("GET", "/api/exam-sessions", staleTimestamp, nonce, "");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/exam-sessions");
        request.setServletPath("/api/exam-sessions");
        request.addHeader("X-Timestamp", staleTimestamp);
        request.addHeader("X-Nonce", nonce);
        request.addHeader("X-Signature", signature);
        request.addHeader("Authorization", "Bearer " + SESSION_TOKEN);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("timestamp"));
    }

    @Test
    void testReplayNonce() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String reusedNonce = "already-used-nonce";
        String signature = computeHmacSignature("GET", "/api/exam-sessions", timestamp, reusedNonce, "");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/exam-sessions");
        request.setServletPath("/api/exam-sessions");
        request.addHeader("X-Timestamp", timestamp);
        request.addHeader("X-Nonce", reusedNonce);
        request.addHeader("X-Signature", signature);
        request.addHeader("Authorization", "Bearer " + SESSION_TOKEN);

        MockHttpServletResponse response = new MockHttpServletResponse();

        when(nonceReplayRepository.existsById(reusedNonce)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Nonce"));
    }

    @Test
    void testMissingHeaders() throws Exception {
        // Only X-Nonce and X-Signature present, missing X-Timestamp
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/exam-sessions");
        request.setServletPath("/api/exam-sessions");
        request.addHeader("X-Nonce", "some-nonce");
        request.addHeader("X-Signature", "some-signature");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("signing"));
    }

    @Test
    void testLoginEndpointSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServletPath("/api/auth/login");

        assertTrue(filter.shouldNotFilter(request));
    }
}
