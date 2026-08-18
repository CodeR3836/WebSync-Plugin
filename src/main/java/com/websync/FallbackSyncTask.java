package com.websync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Every {@code fallback-sync-interval-seconds}, re-syncs every
 * currently online player, regardless of whether anything actually
 * changed. Event-based sync (join/quit/stat changes) covers the common
 * case; this exists purely to recover from missed events, dropped
 * requests, or a website outage that occurred while events fired.
 *
 * It is also the website's proof-of-life for presence: each sync
 * refreshes {@code Player.lastSeenAt}, and the website treats a player
 * whose last sighting is older than its presence TTL as offline. The
 * configured interval must therefore stay comfortably below that TTL.
 *
 * Goes through {@link PlayerSyncQueue} like any other sync so it's
 * still batched into as few HTTP requests as this player count needs,
 * then flushes immediately rather than waiting out the normal
 * event-debounce window.
 */
public final class FallbackSyncTask implements Runnable {

    private final PlayerSyncQueue queue;
    private final PlayerPayloadFactory payloadFactory;

    public FallbackSyncTask(PlayerSyncQueue queue, PlayerPayloadFactory payloadFactory) {
        this.queue = queue;
        this.payloadFactory = payloadFactory;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Object payload = payloadFactory.build(
                    player,
                    PlatformBridge.getPlatform(player),
                    Playtime.minutes(player),
                    RankBridge.getRankSlug(player),
                    // Everything this task iterates is, by definition, connected.
                    true,
                    true);
            queue.enqueue(player.getUniqueId(), payload);
        }
        queue.flushNow();
    }
}
