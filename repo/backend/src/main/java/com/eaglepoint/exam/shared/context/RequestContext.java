package com.eaglepoint.exam.shared.context;

import com.eaglepoint.exam.shared.enums.Role;

/**
 * Thread-local holder for the authenticated user's context during a request.
 * Populated by the security filter chain and cleared after request completion.
 */
public final class RequestContext {

    private static final ThreadLocal<RequestContextData> CONTEXT = new ThreadLocal<>();

    private RequestContext() {
        // utility class
    }

    public static void set(Long userId, String username, Role role,
                           String sessionId, String ipAddress, String traceId) {
        CONTEXT.set(new RequestContextData(userId, username, role, sessionId, ipAddress, traceId));
    }

    public static RequestContextData get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static Long getUserId() {
        RequestContextData data = CONTEXT.get();
        return data != null ? data.userId() : null;
    }

    public static String getUsername() {
        RequestContextData data = CONTEXT.get();
        return data != null ? data.username() : null;
    }

    public static Role getRole() {
        RequestContextData data = CONTEXT.get();
        return data != null ? data.role() : null;
    }

    public static String getSessionId() {
        RequestContextData data = CONTEXT.get();
        return data != null ? data.sessionId() : null;
    }

    public static String getIpAddress() {
        RequestContextData data = CONTEXT.get();
        return data != null ? data.ipAddress() : null;
    }

    public static String getTraceId() {
        RequestContextData data = CONTEXT.get();
        return data != null ? data.traceId() : null;
    }

    /**
     * Immutable record holding the per-request user context.
     */
    public record RequestContextData(
            Long userId,
            String username,
            Role role,
            String sessionId,
            String ipAddress,
            String traceId
    ) {
    }
}
