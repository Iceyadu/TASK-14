package com.eaglepoint.exam.security.filter;

import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Third filter in the chain. Enforces per-user and per-IP rate limits
 * using an in-memory sliding window counter.
 * <p>
 * Limits are configurable via {@code app.rate-limit.user-per-minute} and
 * {@code app.rate-limit.ip-per-minute} properties (defaults: 60 and 300).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L; // 1 minute

    private final int userLimit;
    private final int ipLimit;
    private final ConcurrentHashMap<String, SlidingWindowCounter> userBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SlidingWindowCounter> ipBuckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.user-per-minute:60}") int userLimit,
            @Value("${app.rate-limit.ip-per-minute:300}") int ipLimit) {
        this.objectMapper = objectMapper;
        this.userLimit = userLimit;
        this.ipLimit = ipLimit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SecurityFilter.isAuthLoginPath(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String ipAddress = request.getRemoteAddr();

        // Per-IP rate limit
        SlidingWindowCounter ipCounter = ipBuckets.computeIfAbsent(
                ipAddress, k -> new SlidingWindowCounter());
        if (!ipCounter.tryAcquire(ipLimit)) {
            writeTooManyRequests(response);
            return;
        }

        // Per-user rate limit (only if user is authenticated via RequestContext)
        Long userId = RequestContext.getUserId();
        if (userId != null) {
            String userKey = "user:" + userId;
            SlidingWindowCounter userCounter = userBuckets.computeIfAbsent(
                    userKey, k -> new SlidingWindowCounter());
            if (!userCounter.tryAcquire(userLimit)) {
                writeTooManyRequests(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        ApiResponse<Void> body = ApiResponse.error("Rate limit exceeded. Please try again later.");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /**
     * Simple sliding window counter that resets its count when the current
     * window has elapsed.
     */
    static class SlidingWindowCounter {

        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        private final AtomicInteger count = new AtomicInteger(0);

        /**
         * Attempts to increment the counter. Returns {@code true} if the
         * request is within the allowed limit, {@code false} otherwise.
         */
        boolean tryAcquire(int limit) {
            long now = System.currentTimeMillis();
            long start = windowStart.get();

            // If the window has elapsed, reset
            if (now - start > WINDOW_MILLIS) {
                // CAS to avoid multiple threads resetting simultaneously
                if (windowStart.compareAndSet(start, now)) {
                    count.set(1);
                    return true;
                }
                // Another thread reset the window; fall through to normal increment
            }

            int current = count.incrementAndGet();
            return current <= limit;
        }
    }
}
