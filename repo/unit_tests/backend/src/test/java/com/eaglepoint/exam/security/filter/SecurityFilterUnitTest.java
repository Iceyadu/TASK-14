package com.eaglepoint.exam.security.filter;

import com.eaglepoint.exam.security.model.Session;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.repository.SessionRepository;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.shared.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityFilterUnitTest {

    @Test
    void testSkipsLoginPath() {
        SecurityFilter filter = new SecurityFilter(mock(SessionRepository.class), mock(UserRepository.class), new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void testRejectsMissingAuthHeader() throws Exception {
        SecurityFilter filter = new SecurityFilter(mock(SessionRepository.class), mock(UserRepository.class), new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        assertEquals(401, response.getStatus());
    }

    @Test
    void testAllowsValidSessionAndUser() throws Exception {
        SessionRepository sessionRepository = mock(SessionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        SecurityFilter filter = new SecurityFilter(sessionRepository, userRepository, new ObjectMapper());
        ReflectionTestUtils.setField(filter, "sessionTimeoutMinutes", 30L);
        ReflectionTestUtils.setField(filter, "rememberDeviceDays", 7L);

        Session session = new Session();
        session.setId(1L);
        session.setSessionToken("token-1");
        session.setUserId(10L);
        session.setLastActiveAt(LocalDateTime.now().minusMinutes(1));
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        session.setRememberDevice(false);

        User user = new User();
        user.setId(10L);
        user.setUsername("valid-user");
        user.setRole(Role.ADMIN);

        when(sessionRepository.findBySessionToken("token-1")).thenReturn(Optional.of(session));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("Authorization", "Bearer token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(200));

        assertEquals(200, response.getStatus());
        verify(sessionRepository).save(session);
    }
}
