package com.eaglepoint.exam.security.filter;

import com.eaglepoint.exam.security.model.Session;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.repository.SessionRepository;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * First filter in the chain. Validates the Bearer session token, checks
 * session expiry, refreshes {@code last_active_at}, and populates
 * {@link RequestContext} for downstream code.
 */
public class SecurityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityFilter.class);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${app.security.session-timeout-minutes:30}")
    private long sessionTimeoutMinutes;

    @Value("${app.security.remember-device-days:7}")
    private long rememberDeviceDays;

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public SecurityFilter(SessionRepository sessionRepository,
                          UserRepository userRepository,
                          ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return isAuthLoginPath(request);
    }

    static boolean isAuthLoginPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String path = request.getServletPath();
        return (uri != null && uri.contains("/api/auth/login"))
                || (path != null && path.contains("/api/auth/login"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = UUID.randomUUID().toString();

        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response, "Empty session token");
            return;
        }

        Optional<Session> sessionOpt = sessionRepository.findBySessionToken(token);
        if (sessionOpt.isEmpty()) {
            writeUnauthorized(response, "Invalid session token");
            return;
        }

        Session session = sessionOpt.get();
        LocalDateTime now = LocalDateTime.now();

        // Check hard expiry
        if (session.getExpiresAt() != null && now.isAfter(session.getExpiresAt())) {
            writeUnauthorized(response, "Session expired");
            return;
        }

        // Check inactivity timeout
        long timeoutMinutes = session.isRememberDevice()
                ? rememberDeviceDays * 24 * 60
                : sessionTimeoutMinutes;

        if (session.getLastActiveAt() != null) {
            long minutesSinceActive = ChronoUnit.MINUTES.between(session.getLastActiveAt(), now);
            if (minutesSinceActive > timeoutMinutes) {
                writeUnauthorized(response, "Session timed out due to inactivity");
                return;
            }
        }

        // Look up user
        Optional<User> userOpt = userRepository.findById(session.getUserId());
        if (userOpt.isEmpty()) {
            writeUnauthorized(response, "User associated with session not found");
            return;
        }
        User user = userOpt.get();

        // Update last active timestamp
        session.setLastActiveAt(now);
        sessionRepository.save(session);

        // Populate request context
        String ipAddress = request.getRemoteAddr();
        RequestContext.set(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                session.getSessionToken(),
                ipAddress,
                traceId
        );

        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.error(message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
