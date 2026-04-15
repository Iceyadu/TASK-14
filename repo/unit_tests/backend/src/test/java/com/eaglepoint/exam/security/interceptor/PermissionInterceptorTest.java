package com.eaglepoint.exam.security.interceptor;

import com.eaglepoint.exam.security.annotation.RequirePermission;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Permission;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PermissionInterceptorTest {

    static class MethodAnnotatedController {
        @RequirePermission(Permission.USER_MANAGE)
        public void guardedMethod() {}
    }

    @RequirePermission(Permission.SESSION_VIEW)
    static class ClassAnnotatedController {
        public void guardedByClass() {}
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void testAllowsMethodLevelPermission() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor();
        RequestContext.set(1L, "admin", Role.ADMIN, "s1", "127.0.0.1", "t1");

        Method method = MethodAnnotatedController.class.getMethod("guardedMethod");
        HandlerMethod handlerMethod = new HandlerMethod(new MethodAnnotatedController(), method);

        boolean allowed = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handlerMethod);
        assertTrue(allowed);
    }

    @Test
    void testAllowsClassLevelPermission() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor();
        RequestContext.set(2L, "teacher", Role.HOMEROOM_TEACHER, "s2", "127.0.0.1", "t2");

        Method method = ClassAnnotatedController.class.getMethod("guardedByClass");
        HandlerMethod handlerMethod = new HandlerMethod(new ClassAnnotatedController(), method);

        boolean allowed = interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handlerMethod);
        assertTrue(allowed);
    }

    @Test
    void testDeniesWhenRoleLacksPermission() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor();
        RequestContext.set(3L, "student", Role.STUDENT, "s3", "127.0.0.1", "t3");

        Method method = MethodAnnotatedController.class.getMethod("guardedMethod");
        HandlerMethod handlerMethod = new HandlerMethod(new MethodAnnotatedController(), method);

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handlerMethod));
    }

    @Test
    void testDeniesWhenNoAuthenticatedRole() throws Exception {
        PermissionInterceptor interceptor = new PermissionInterceptor();
        Method method = MethodAnnotatedController.class.getMethod("guardedMethod");
        HandlerMethod handlerMethod = new HandlerMethod(new MethodAnnotatedController(), method);

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), handlerMethod));
    }
}
