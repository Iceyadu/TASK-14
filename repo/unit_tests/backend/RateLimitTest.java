package com.eaglepoint.exam.security.filter;

import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RateLimitFilter} covering per-user and per-IP rate limiting.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitTest {

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        rateLimitFilter = new RateLimitFilter(objectMapper, 60, 300);
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private MockHttpServletRequest createRequest(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/exam-sessions");
        request.setServletPath("/api/exam-sessions");
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void testUserRateLimitAllowed() throws Exception {
        RequestContext.set(1L, "user1", Role.HOMEROOM_TEACHER, "session-1", "10.0.0.1", "trace-1");

        // 60 requests should all pass
        for (int i = 0; i < 60; i++) {
            MockHttpServletRequest request = createRequest("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            rateLimitFilter.doFilterInternal(request, response, filterChain);

            assertNotEquals(429, response.getStatus(), "Request " + (i + 1) + " should not be rate limited");
        }

        verify(filterChain, times(60)).doFilter(any(), any());
    }

    @Test
    void testUserRateLimitExceeded() throws Exception {
        RequestContext.set(2L, "user2", Role.HOMEROOM_TEACHER, "session-2", "10.0.0.2", "trace-2");

        // Send 61 requests; the 61st should be blocked
        MockHttpServletResponse lastResponse = null;
        for (int i = 0; i < 61; i++) {
            MockHttpServletRequest request = createRequest("10.0.0.2");
            lastResponse = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, lastResponse, filterChain);
        }

        assertNotNull(lastResponse);
        assertEquals(429, lastResponse.getStatus());
        assertTrue(lastResponse.getContentAsString().contains("Rate limit exceeded"));
    }

    @Test
    void testIpRateLimitExceeded() throws Exception {
        // No user context (unauthenticated), same IP, hit 301 requests
        MockHttpServletResponse lastResponse = null;
        for (int i = 0; i < 301; i++) {
            MockHttpServletRequest request = createRequest("10.0.0.99");
            lastResponse = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, lastResponse, filterChain);
        }

        assertNotNull(lastResponse);
        assertEquals(429, lastResponse.getStatus());
    }

    @Test
    void testDifferentUsersIndependent() throws Exception {
        // User A hits 60 requests (at limit)
        RequestContext.set(10L, "userA", Role.HOMEROOM_TEACHER, "s-A", "10.0.0.10", "t-A");
        for (int i = 0; i < 60; i++) {
            MockHttpServletRequest request = createRequest("10.0.0.10");
            MockHttpServletResponse response = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }

        // User B should still be allowed
        RequestContext.set(20L, "userB", Role.HOMEROOM_TEACHER, "s-B", "10.0.0.20", "t-B");
        MockHttpServletRequest requestB = createRequest("10.0.0.20");
        MockHttpServletResponse responseB = new MockHttpServletResponse();
        rateLimitFilter.doFilterInternal(requestB, responseB, filterChain);

        assertNotEquals(429, responseB.getStatus(), "User B should not be rate limited by User A's requests");
    }
}
