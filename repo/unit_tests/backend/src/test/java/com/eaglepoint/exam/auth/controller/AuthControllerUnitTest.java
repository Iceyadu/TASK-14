package com.eaglepoint.exam.auth.controller;

import com.eaglepoint.exam.auth.service.AuthService;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerUnitTest {

    @Mock private AuthService authService;
    @Mock private com.eaglepoint.exam.auth.repository.ManagedDeviceRepository managedDeviceRepository;
    @Mock private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void testGetSessionReturnsUserAndSessionId() {
        AuthController controller = new AuthController(authService, managedDeviceRepository, userRepository);
        RequestContext.set(101L, "auth_unit_user", Role.ADMIN, "sess-101", "127.0.0.1", "trace-1");

        User user = new User();
        user.setId(101L);
        user.setUsername("auth_unit_user");
        user.setRole(Role.ADMIN);
        when(userRepository.findById(101L)).thenReturn(Optional.of(user));

        var response = controller.getSession();
        assertNotNull(response.getBody());
        assertEquals("sess-101", response.getBody().getData().get("sessionId"));
        verify(userRepository).findById(101L);
    }

    @Test
    void testLogoutDelegatesToAuthService() {
        AuthController controller = new AuthController(authService, managedDeviceRepository, userRepository);
        controller.logout();
        verify(authService).logout();
    }
}
