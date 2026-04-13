package com.eaglepoint.exam.security.interceptor;

import com.eaglepoint.exam.security.annotation.RequirePermission;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Permission;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.enums.RolePermissions;
import com.eaglepoint.exam.shared.exception.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Intercepts requests to controller methods annotated with
 * {@link RequirePermission} and verifies that the current user's role
 * grants at least one of the required permissions.
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }
        if (annotation == null) {
            return true;
        }

        Role role = RequestContext.getRole();
        if (role == null) {
            throw new AccessDeniedException("Authentication required");
        }

        Set<Permission> granted = RolePermissions.getPermissions(role);
        for (Permission required : annotation.value()) {
            if (granted.contains(required)) {
                return true;
            }
        }

        throw new AccessDeniedException(
                "User role " + role + " does not have the required permission");
    }
}
