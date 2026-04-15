package com.eaglepoint.exam.integration;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

/**
 * Test utility for computing HMAC-SHA256 request signatures matching the
 * production RequestSigningFilter verification logic. Integration tests
 * should use this to exercise the real signing verification path.
 */
public final class SigningTestHelper {

    private SigningTestHelper() {
    }

    /**
     * Adds request signing headers (X-Timestamp, X-Nonce, X-Signature)
     * to the given MockMvc request builder.
     *
     * @param builder    the MockMvc request builder
     * @param signingKey the session signing key (returned from login)
     * @param method     HTTP method (GET, POST, PUT, DELETE)
     * @param path       the request path (e.g. /api/rosters)
     * @param body       the request body string, or empty string for no body
     * @return the builder with signing headers added
     */
    public static MockHttpServletRequestBuilder sign(
            MockHttpServletRequestBuilder builder,
            String signingKey, String method, String path, String body) {

        String servletPath = path;
        int queryIdx = path.indexOf('?');
        if (queryIdx >= 0) {
            servletPath = path.substring(0, queryIdx);
        }
        // Ensure MockMvc request exposes the same servletPath that the production
        // signing filter uses for canonical signature verification.
        builder.servletPath(servletPath);

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String bodyHash = sha256Hex(body != null ? body : "");
        String signingString = method.toUpperCase() + "\n" + path + "\n"
                + timestamp + "\n" + nonce + "\n" + bodyHash;
        String signature = hmacSha256Hex(signingString, signingKey);

        return builder
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature);
    }

    private static String hmacSha256Hex(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }

    private static String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
