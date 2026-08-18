package com.websync;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Queues a player-sync snapshot on join/quit (and counts placed
 * blocks). All listener methods run on the main thread (Bukkit
 * requirement), but only enqueue into {@link PlayerSyncQueue} — the
 * actual HTTP work happens later, off-thread, in a batched flush.
 *
 * Join and quit also ask for an out-of-band heartbeat
 * ({@code presenceChanged}), because the website's player *count* comes
 * only from the heartbeat: without this, a join would not be reflected
 * until the next scheduled heartbeat, up to a full interval later.
 */
public final class PlayerSyncListener implements Listener {

    private final PlayerSyncQueue queue;
    private final PlayerPayloadFactory payloadFactory;
    private final PlacedBlocksStore placedBlocksStore;
    private final Runnable presenceChanged;

    public PlayerSyncListener(
            PlayerSyncQueue queue, PlayerPayloadFactory payloadFactory,
            PlacedBlocksStore placedBlocksStore, Runnable presenceChanged) {
        this.queue = queue;
        this.payloadFactory = payloadFactory;
        this.placedBlocksStore = placedBlocksStore;
        this.presenceChanged = presenceChanged;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        enqueue(event.getPlayer(), true);
        presenceChanged.run();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // The quitting player is still in the server's player list while
        // this event runs (at every priority, MONITOR included), so the
        // offline state has to be stated explicitly — see
        // PlayerPayloadFactory#build.
        enqueue(event.getPlayer(), false);
        presenceChanged.run();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        placedBlocksStore.increment(event.getPlayer().getUniqueId());
    }

    private void enqueue(Player player, boolean online) {
        Object payload = payloadFactory.build(
                player,
                PlatformBridge.getPlatform(player),
                Playtime.minutes(player),
                RankBridge.getRankSlug(player),
                online,
                true);
        queue.enqueue(player.getUniqueId(), payload);
    }
}
