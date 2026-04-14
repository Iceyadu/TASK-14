package com.eaglepoint.exam.security.filter;

import com.eaglepoint.exam.security.model.NonceReplay;
import com.eaglepoint.exam.security.model.Session;
import com.eaglepoint.exam.security.repository.NonceReplayRepository;
import com.eaglepoint.exam.security.repository.SessionRepository;
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
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Second filter in the chain. Validates HMAC-SHA256 request signatures to
 * ensure request integrity and prevent replay attacks.
 * <p>
 * Expected headers: {@code X-Timestamp}, {@code X-Nonce}, {@code X-Signature}.
 * <p>
 * Signature is computed over: method + "\n" + path + "\n" + timestamp + "\n" +
 * nonce + "\n" + SHA256(body). The path is the servlet path plus the raw query
 * string when present (same canonical string the SPA client signs).
 */
public class RequestSigningFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestSigningFilter.class);

    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_NONCE = "X-Nonce";
    private static final String HEADER_SIGNATURE = "X-Signature";
    private static final long MAX_TIMESTAMP_DRIFT_SECONDS = 120;
    private static final long NONCE_EXPIRY_SECONDS = 120;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SessionRepository sessionRepository;
    private final NonceReplayRepository nonceReplayRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.security.require-request-signing:true}")
    private boolean requireRequestSigning;

    public RequestSigningFilter(SessionRepository sessionRepository,
                                NonceReplayRepository nonceReplayRepository,
                                ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.nonceReplayRepository = nonceReplayRepository;
        this.objectMapper = objectMapper;
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

        if (!requireRequestSigning) {
            filterChain.doFilter(request, response);
            return;
        }

        String timestampStr = request.getHeader(HEADER_TIMESTAMP);
        String nonce = request.getHeader(HEADER_NONCE);
        String signature = request.getHeader(HEADER_SIGNATURE);

        // All authenticated API calls must include signing headers (login is excluded via shouldNotFilter)
        if (timestampStr == null || nonce == null || signature == null) {
            writeUnauthorized(response, "Request signing headers required (X-Timestamp, X-Nonce, X-Signature)");
            return;
        }

        // Validate timestamp drift
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            writeUnauthorized(response, "Invalid X-Timestamp header");
            return;
        }

        long nowEpochSeconds = Instant.now().getEpochSecond();
        if (Math.abs(nowEpochSeconds - timestamp) > MAX_TIMESTAMP_DRIFT_SECONDS) {
            writeUnauthorized(response, "Request timestamp is outside acceptable window");
            return;
        }

        // Check nonce replay
        if (nonceReplayRepository.existsById(nonce)) {
            writeUnauthorized(response, "Nonce has already been used");
            return;
        }

        // Get session signing key from Bearer token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "Missing Authorization header for signing verification");
            return;
        }
        String token = authHeader.substring(7).trim();
        Optional<Session> sessionOpt = sessionRepository.findBySessionToken(token);
        if (sessionOpt.isEmpty()) {
            writeUnauthorized(response, "Invalid session for signing verification");
            return;
        }
        Session session = sessionOpt.get();
        String signingKey = session.getSigningKey();
        if (signingKey == null || signingKey.isEmpty()) {
            writeUnauthorized(response, "No signing key associated with this session");
            return;
        }

        // Read body for signature computation
        ContentCachingRequestWrapper wrappedRequest;
        if (request instanceof ContentCachingRequestWrapper) {
            wrappedRequest = (ContentCachingRequestWrapper) request;
        } else {
            wrappedRequest = new ContentCachingRequestWrapper(request);
        }

        // We need to read the body to compute the hash. For ContentCachingRequestWrapper
        // the content is available after the filter chain, so we force a read.
        byte[] body = wrappedRequest.getInputStream().readAllBytes();
        String bodyHash = sha256Hex(body);

        // Build signing string (path must match client: servlet path + raw query string, if any)
        String method = wrappedRequest.getMethod();
        String path = canonicalSigningPath(wrappedRequest);
        String signingString = method + "\n" + path + "\n" + timestampStr + "\n" + nonce + "\n" + bodyHash;

        // Compute expected signature
        String expectedSignature;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    signingKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(signingString.getBytes(StandardCharsets.UTF_8));
            expectedSignature = bytesToHex(hmacBytes);
        } catch (Exception e) {
            log.error("Failed to compute HMAC signature", e);
            writeUnauthorized(response, "Signature verification failed");
            return;
        }

        // Constant-time comparison (client sends lowercase hex HMAC, same as CryptoJS default)
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            writeUnauthorized(response, "Invalid request signature");
            return;
        }

        // Store nonce
        LocalDateTime nonceExpiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(nowEpochSeconds + NONCE_EXPIRY_SECONDS), ZoneOffset.UTC);
        nonceReplayRepository.save(new NonceReplay(nonce, nonceExpiresAt));

        filterChain.doFilter(wrappedRequest, response);
    }

    /**
     * Same canonical path the Vue client signs: {@code pathname + search} (search includes {@code ?}).
     */
    static String canonicalSigningPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath == null) {
            servletPath = "";
        }
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            return servletPath + "?" + query;
        }
        return servletPath;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.error(message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
