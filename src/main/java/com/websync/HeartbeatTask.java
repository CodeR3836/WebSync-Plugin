package com.websync;

import com.websync.json.JsonWriter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Builds and sends the ~30-second server heartbeat: status, player
 * counts, version, average ping, and TPS (when available).
 *
 * Reads Bukkit/Paper's own in-memory fields (online player count, max
 * players, version, TPS) — all cheap, synchronous, main-thread-safe
 * reads. The actual HTTP request is dispatched async by
 * {@link WebSyncHttpClient}, so this never blocks the main thread on
 * network I/O.
 */
public final class HeartbeatTask implements Runnable {

    private final WebSyncSettings settings;
    private final WebSyncHttpClient httpClient;

    public HeartbeatTask(WebSyncSettings settings, WebSyncHttpClient httpClient) {
        this.settings = settings;
        this.httpClient = httpClient;
    }

    @Override
    public void run() {
        int playersOnline = Bukkit.getOnlinePlayers().size();
        int playersMax = Bukkit.getMaxPlayers();
        String version = Bukkit.getVersion();

        Integer pingMs = averagePingMs();
        Double tps = readTps();

        Object payload = JsonWriter.obj(
                "status", "ONLINE",
                "playersOnline", playersOnline,
                "playersMax", playersMax,
                "version", version,
                "pingMs", pingMs,
                "tps", tps
        );

        httpClient.post("/api/websync/heartbeat", JsonWriter.write(payload));
    }

    private Integer averagePingMs() {
        var online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) return null;
        long total = 0;
        for (Player player : online) {
            total += Math.max(0, player.getPing());
        }
        return (int) (total / online.size());
    }

    /** Paper exposes {@code Bukkit.getTPS()}; not guaranteed on every fork,
     *  so this fails soft to {@code null} ("omit") rather than a bogus value. */
    private Double readTps() {
        try {
            double[] tps = Bukkit.getTPS();
            if (tps == null || tps.length == 0) return null;
            // Clamp: a server can briefly report a synthetic value above 20
            // right after startup; never forward something implausible.
            return Math.max(0.0, Math.min(tps[0], 20.0));
        } catch (Throwable notAvailable) {
            return null;
        }
    }
}
