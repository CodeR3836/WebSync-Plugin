package com.websync;

import com.websync.json.JsonWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Combines player-sync events (join/quit/stat changes) that happen
 * close together into a single {@code POST /api/websync/players}
 * request, instead of one HTTP call per event.
 *
 * Multiple updates queued for the same player before a flush simply
 * overwrite each other (last write wins) — each queued entry is
 * already a complete snapshot of that player's current state, not a
 * delta, so this can never lose information a flush would have sent.
 */
public final class PlayerSyncQueue {

    private final Logger logger;
    private final WebSyncSettings settings;
    private final WebSyncHttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    private final Map<UUID, Object> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> pendingFlush;

    public PlayerSyncQueue(
            Logger logger, WebSyncSettings settings, WebSyncHttpClient httpClient,
            ScheduledExecutorService scheduler) {
        this.logger = logger;
        this.settings = settings;
        this.httpClient = httpClient;
        this.scheduler = scheduler;
    }

    /** Queues a full snapshot of one player's current state for the next batch flush. */
    public void enqueue(UUID uuid, Object playerJson) {
        pending.put(uuid, playerJson);
        if (flushScheduled.compareAndSet(false, true)) {
            pendingFlush = scheduler.schedule(this::flush, settings.batchWindowMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /** Immediately sends whatever is queued (used by fallback sync and shutdown). */
    public void flushNow() {
        ScheduledFuture<?> scheduled = pendingFlush;
        if (scheduled != null) scheduled.cancel(false);
        flush();
    }

    private void flush() {
        flushScheduled.set(false);
        if (pending.isEmpty()) return;

        List<Object> batch = new ArrayList<>(pending.values());
        pending.clear();

        String body = JsonWriter.write(JsonWriter.obj("players", batch));
        logger.fine("[WebSync] Flushing batch of " + batch.size() + " player update(s).");
        httpClient.post("/api/websync/players", body).thenAccept(ok -> {
            if (ok) {
                logger.fine("[WebSync] Synced " + batch.size() + " player(s).");
            }
            // On failure, WebSyncHttpClient has already logged + exhausted its
            // own retries. We deliberately don't re-queue these players here:
            // the periodic fallback sync (every fallback-sync-interval-seconds)
            // will pick up any player still online and repair the website's
            // state, which is simpler and safer than a second retry layer
            // stacked on top of the HTTP client's own.
        });
    }
}
