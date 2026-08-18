package com.websync;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed, immutable snapshot of {@code config.yml}.
 *
 * Read once at startup / on {@code /websync reload} rather than pulling
 * from {@link FileConfiguration} scattered across the plugin — keeps
 * every consumer looking at a single consistent view even if reload
 * happens mid-flight.
 */
public final class WebSyncSettings {

    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String signingSecret;
    private final int heartbeatIntervalSeconds;
    private final int fallbackSyncIntervalSeconds;
    private final long batchWindowMillis;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;
    private final int retryMaxAttempts;
    private final int retryBaseDelaySeconds;
    private final int retryMaxDelaySeconds;
    private final String serverSlug;

    private WebSyncSettings(Builder b) {
        this.enabled = b.enabled;
        this.baseUrl = b.baseUrl;
        this.apiKey = b.apiKey;
        this.signingSecret = b.signingSecret;
        this.heartbeatIntervalSeconds = b.heartbeatIntervalSeconds;
        this.fallbackSyncIntervalSeconds = b.fallbackSyncIntervalSeconds;
        this.batchWindowMillis = b.batchWindowMillis;
        this.connectTimeoutSeconds = b.connectTimeoutSeconds;
        this.readTimeoutSeconds = b.readTimeoutSeconds;
        this.retryMaxAttempts = b.retryMaxAttempts;
        this.retryBaseDelaySeconds = b.retryBaseDelaySeconds;
        this.retryMaxDelaySeconds = b.retryMaxDelaySeconds;
        this.serverSlug = b.serverSlug;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    public static WebSyncSettings load(FileConfiguration config) {
        Builder b = new Builder();
        b.enabled = config.getBoolean("websync.enabled", true);
        b.baseUrl = trimTrailingSlash(config.getString("websync.base-url", ""));
        b.apiKey = config.getString("websync.api-key", "");
        b.signingSecret = config.getString("websync.signing-secret", "");
        b.heartbeatIntervalSeconds = Math.max(5, config.getInt("heartbeat-interval-seconds", 30));
        // Clamped to [15, 120]. The upper bound is a protocol contract, not
        // a preference: the website expires a player's presence when their
        // last sighting is older than 150s, and this task is what refreshes
        // it. A longer interval (including the 300 that older config.yml
        // files on disk still carry, since saveDefaultConfig never
        // overwrites them) would make online players flicker offline.
        b.fallbackSyncIntervalSeconds = clamp(
                config.getInt("fallback-sync-interval-seconds", 60), 15, 120);
        b.batchWindowMillis = Math.max(0, config.getLong("batch-window-millis", 2000));
        b.connectTimeoutSeconds = Math.max(1, config.getInt("http.connect-timeout-seconds", 5));
        b.readTimeoutSeconds = Math.max(1, config.getInt("http.read-timeout-seconds", 10));
        b.retryMaxAttempts = Math.max(1, config.getInt("retry.max-attempts", 5));
        b.retryBaseDelaySeconds = Math.max(1, config.getInt("retry.base-delay-seconds", 1));
        b.retryMaxDelaySeconds = Math.max(b.retryBaseDelaySeconds, config.getInt("retry.max-delay-seconds", 30));
        b.serverSlug = config.getString("server-slug", "main");
        return new WebSyncSettings(b);
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** True only when enabled AND the required secrets/URL are actually present. */
    public boolean isUsable() {
        return enabled
                && baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && signingSecret != null && !signingSecret.isBlank();
    }

    public boolean isEnabled() { return enabled; }
    public String baseUrl() { return baseUrl; }
    public String apiKey() { return apiKey; }
    public String signingSecret() { return signingSecret; }
    public int heartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    public int fallbackSyncIntervalSeconds() { return fallbackSyncIntervalSeconds; }
    public long batchWindowMillis() { return batchWindowMillis; }
    public int connectTimeoutSeconds() { return connectTimeoutSeconds; }
    public int readTimeoutSeconds() { return readTimeoutSeconds; }
    public int retryMaxAttempts() { return retryMaxAttempts; }
    public int retryBaseDelaySeconds() { return retryBaseDelaySeconds; }
    public int retryMaxDelaySeconds() { return retryMaxDelaySeconds; }
    public String serverSlug() { return serverSlug; }

    private static final class Builder {
        boolean enabled;
        String baseUrl;
        String apiKey;
        String signingSecret;
        int heartbeatIntervalSeconds;
        int fallbackSyncIntervalSeconds;
        long batchWindowMillis;
        int connectTimeoutSeconds;
        int readTimeoutSeconds;
        int retryMaxAttempts;
        int retryBaseDelaySeconds;
        int retryMaxDelaySeconds;
        String serverSlug;
    }
}
