package com.websync;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

/**
 * Sends signed WebSync requests to the website, asynchronously, with
 * bounded exponential backoff on transient failures.
 *
 * Never called from the Minecraft main thread — every public method
 * returns a {@link CompletableFuture} and does its work on
 * {@code httpClient}'s own executor / the plugin's scheduled executor.
 */
public final class WebSyncHttpClient {

    private final Logger logger;
    private final WebSyncSettings settings;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    public WebSyncHttpClient(Logger logger, WebSyncSettings settings, ScheduledExecutorService scheduler) {
        this.logger = logger;
        this.settings = settings;
        this.scheduler = scheduler;
        this.httpClient = HttpClient.newBuilder()
                // Pinned to HTTP/1.1 on purpose. Java's HttpClient defaults to
                // HTTP_2, which over cleartext http:// attempts an h2c upgrade by
                // adding "Upgrade: h2c" + "Connection: Upgrade, HTTP2-Settings"
                // to the request. Node/Next.js HTTP servers (and several
                // reverse proxies) treat that as a protocol-upgrade request and
                // destroy the socket instead of answering it, so the client sees
                // "HTTP/1.1 header parser received no bytes" / EOFException
                // before any response headers — the request never reaches the
                // route handler at all. HTTP/1.1 is what the website speaks.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(settings.connectTimeoutSeconds()))
                .build();
    }

    /**
     * POSTs {@code jsonBody} to {@code path} (e.g. {@code /api/websync/heartbeat}),
     * signing it per the Phase 4A protocol, retrying transient failures with
     * bounded exponential backoff. Every attempt (including retries) uses a
     * fresh request id and timestamp — WebSync request ids are single-use,
     * so reusing one on retry would make the retry itself look like a replay.
     *
     * Completes with {@code true} on any 2xx response, {@code false} if every
     * attempt was exhausted or a permanent (non-retryable) error was hit.
     * Never throws — network/auth/validation failures are logged and
     * swallowed so a WebSync outage can never destabilize the server.
     */
    public CompletableFuture<Boolean> post(String path, String jsonBody) {
        return attempt(path, jsonBody, 1);
    }

    private CompletableFuture<Boolean> attempt(String path, String jsonBody, int attemptNumber) {
        String requestId = WebSyncSigner.newRequestId();
        String timestamp = WebSyncSigner.currentTimestamp();
        String canonical = WebSyncSigner.canonicalMessage("POST", path, timestamp, requestId, jsonBody);
        String signature = WebSyncSigner.sign(settings.signingSecret(), canonical);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(settings.baseUrl() + path))
                .timeout(Duration.ofSeconds(settings.readTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("x-websync-key", settings.apiKey())
                .header("x-websync-signature", signature)
                .header("x-websync-timestamp", timestamp)
                .header("x-websync-request-id", requestId)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        return handleFailure(path, jsonBody, attemptNumber, null,
                                describeNetworkError(throwable));
                    }
                    int status = response.statusCode();
                    if (status >= 200 && status < 300) {
                        logger.fine("[WebSync] " + path + " synced (HTTP " + status + ")");
                        return CompletableFuture.completedFuture(true);
                    }
                    return handleFailure(path, jsonBody, attemptNumber, status, null);
                })
                .thenCompose(f -> f);
    }

    private CompletableFuture<Boolean> handleFailure(
            String path, String jsonBody, int attemptNumber, Integer status, String networkError) {

        boolean permanent = status != null && isPermanentFailure(status);

        if (status != null) {
            if (status == 401) {
                logger.warning("[WebSync] Authentication failed for " + path
                        + "; check api-key/signing-secret in config.yml.");
            } else if (permanent) {
                logger.warning("[WebSync] Request to " + path + " rejected (HTTP " + status
                        + "); not retrying.");
            }
        } else {
            logger.warning("[WebSync] Request to " + path + " failed: " + networkError);
        }

        if (permanent || attemptNumber >= settings.retryMaxAttempts()) {
            if (!permanent) {
                logger.warning("[WebSync] Giving up on " + path + " after " + attemptNumber + " attempts.");
            }
            return CompletableFuture.completedFuture(false);
        }

        long delaySeconds = Math.min(
                settings.retryBaseDelaySeconds() * (1L << (attemptNumber - 1)),
                settings.retryMaxDelaySeconds());

        logger.info("[WebSync] Request to " + path + " failed"
                + (status != null ? " (HTTP " + status + ")" : "")
                + ", retrying in " + delaySeconds + "s (attempt " + (attemptNumber + 1)
                + "/" + settings.retryMaxAttempts() + ")");

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        scheduler.schedule(() -> attempt(path, jsonBody, attemptNumber + 1).whenComplete((ok, err) -> {
            if (err != null) {
                result.completeExceptionally(err);
            } else {
                result.complete(ok);
            }
        }), delaySeconds, java.util.concurrent.TimeUnit.SECONDS);
        return result;
    }

    /** 4xx errors other than 408/429 mean the request itself is wrong (bad
     *  auth, bad payload, replay) — retrying an identical-in-spirit request
     *  won't fix that, only a fresh attempt with corrected input would. */
    private static boolean isPermanentFailure(int status) {
        if (status == 408 || status == 429) return false;
        return status >= 400 && status < 500;
    }

    private static String describeNetworkError(Throwable t) {
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        if (cause instanceof java.net.http.HttpTimeoutException) return "timed out";
        if (cause instanceof IOException) return "network error (" + cause.getClass().getSimpleName() + ")";
        return cause.getClass().getSimpleName();
    }
}
