package com.websync;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Implements the WebSync Phase 4A signing protocol exactly as the
 * website's {@code src/lib/websync/auth.ts} verifies it. This is the
 * ONLY signing scheme WebSync uses — do not invent a second one.
 *
 * Canonical message (newline-joined):
 * <pre>
 * METHOD
 * PATH
 * TIMESTAMP
 * REQUEST_ID
 * BODY
 * </pre>
 * signed with HMAC-SHA256, hex-encoded. The body signed is the exact
 * byte sequence transmitted on the wire, never a reformatted/reparsed
 * version of it.
 */
public final class WebSyncSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private WebSyncSigner() {
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    public static String currentTimestamp() {
        return Long.toString(System.currentTimeMillis());
    }

    /** Builds the exact newline-joined canonical message that gets signed. */
    public static String canonicalMessage(
            String method, String path, String timestamp, String requestId, String body) {
        return method.toUpperCase(java.util.Locale.ROOT)
                + "\n" + path
                + "\n" + timestamp
                + "\n" + requestId
                + "\n" + body;
    }

    /** Hex-encoded HMAC-SHA256 of {@code canonicalMessage} using {@code signingSecret}. */
    public static String sign(String signingSecret, String canonicalMessage) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(canonicalMessage.getBytes(StandardCharsets.UTF_8));
            return toHex(raw);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is a JDK-guaranteed algorithm; this can only happen
            // from a misuse bug (e.g. an empty key), never a runtime/env issue.
            throw new IllegalStateException("Failed to compute WebSync HMAC signature", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
