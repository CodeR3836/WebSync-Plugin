package com.websync;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WebSyncPlugin extends JavaPlugin {

    private WebSyncSettings settings;
    private ScheduledExecutorService scheduler;
    private WebSyncHttpClient httpClient;
    private PlacedBlocksStore placedBlocksStore;
    private PlayerPayloadFactory payloadFactory;
    private PlayerSyncQueue queue;

    private BukkitTask heartbeatTask;
    private BukkitTask fallbackTask;

    private HeartbeatTask heartbeat;
    /** Guards against one heartbeat per player when a group joins at once. */
    private final AtomicBoolean immediateHeartbeatPending = new AtomicBoolean(false);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.settings = WebSyncSettings.load(getConfig());

        // A dedicated scheduler for HTTP retry backoff delays — kept
        // separate from Bukkit's own scheduler since these callbacks must
        // keep running (to eventually complete or give up) independent of
        // server tick timing, and must never touch Bukkit API off-thread.
        this.scheduler = Executors.newScheduledThreadPool(2, runnable -> {
            Thread t = new Thread(runnable, "WebSync-Scheduler");
            t.setDaemon(true);
            return t;
        });

        this.httpClient = new WebSyncHttpClient(getLogger(), settings, scheduler);
        this.placedBlocksStore = new PlacedBlocksStore(getLogger(), getDataFolder());
        this.placedBlocksStore.load();
        this.payloadFactory = new PlayerPayloadFactory(placedBlocksStore);
        this.queue = new PlayerSyncQueue(getLogger(), settings, httpClient, scheduler);

        // Vault is a soft-depend (see plugin.yml), so if it's present it's
        // already fully loaded here, along with whatever economy plugin
        // registered with it — safe no-op otherwise.
        VaultEconomySupport.setup(getLogger());

        if (!settings.isUsable()) {
            getLogger().warning("[WebSync] Not configured (missing base-url/api-key/signing-secret) "
                    + "or disabled — sync will not run until config.yml is filled in and the plugin is reloaded.");
        } else {
            getLogger().info("[WebSync] Connected configuration loaded for " + settings.baseUrl());
        }

        getServer().getPluginManager().registerEvents(
                new PlayerSyncListener(
                        queue, payloadFactory, placedBlocksStore, this::requestImmediateHeartbeat),
                this);

        var websyncCommand = getCommand("websync");
        if (websyncCommand != null) {
            websyncCommand.setExecutor(new WebSyncCommand(this));
        }

        startScheduledTasks();

        // Periodically flush placed-blocks counters to disk so a crash
        // loses at most one save interval of local progress.
        getServer().getScheduler().runTaskTimerAsynchronously(this, placedBlocksStore::save, 20L * 60, 20L * 60);
    }

    @Override
    public void onDisable() {
        if (heartbeatTask != null) heartbeatTask.cancel();
        if (fallbackTask != null) fallbackTask.cancel();

        // Best-effort, bounded flush of anything still queued — never
        // delay shutdown indefinitely waiting on a WebSync round trip.
        if (queue != null && settings != null && settings.isUsable()) {
            queue.flushNow();
        }
        if (placedBlocksStore != null) {
            placedBlocksStore.save();
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void startScheduledTasks() {
        if (!settings.isUsable()) return;

        this.heartbeat = new HeartbeatTask(settings, httpClient);

        long heartbeatTicks = settings.heartbeatIntervalSeconds() * 20L;
        heartbeatTask = getServer().getScheduler().runTaskTimer(
                this, heartbeat, heartbeatTicks, heartbeatTicks);

        long fallbackTicks = settings.fallbackSyncIntervalSeconds() * 20L;
        fallbackTask = getServer().getScheduler().runTaskTimer(
                this, new FallbackSyncTask(queue, payloadFactory), fallbackTicks, fallbackTicks);
    }

    /** Reloads config.yml and re-applies it. Intervals only take effect on
     *  the next scheduled-task restart (tasks are re-created from scratch). */
    public void reloadWebSync() {
        reloadConfig();
        this.settings = WebSyncSettings.load(getConfig());

        if (heartbeatTask != null) heartbeatTask.cancel();
        if (fallbackTask != null) fallbackTask.cancel();
        startScheduledTasks();
    }

    /**
     * Sends one extra heartbeat shortly after a presence change, so the
     * website's player count follows a join/quit within about a second
     * instead of waiting out {@code heartbeat-interval-seconds}.
     *
     * Runs one tick later, on the main thread, for two reasons: a quit's
     * count must be read *after* the player has actually left the player
     * list, and {@link HeartbeatTask} reads Bukkit state that is only
     * safe to touch there. Coalesced through
     * {@code immediateHeartbeatPending}, so twenty simultaneous joins
     * still produce exactly one additional request.
     */
    public void requestImmediateHeartbeat() {
        if (heartbeat == null || settings == null || !settings.isUsable()) return;
        // Scheduling is rejected once the plugin is disabling (server
        // shutdown fires a quit for every player); the final flush in
        // onDisable covers that case instead.
        if (!isEnabled()) return;
        if (!immediateHeartbeatPending.compareAndSet(false, true)) return;

        getServer().getScheduler().runTaskLater(this, () -> {
            immediateHeartbeatPending.set(false);
            heartbeat.run();
        }, 20L);
    }

    public void runFallbackSyncNow() {
        new FallbackSyncTask(queue, payloadFactory).run();
    }

    public WebSyncSettings settings() {
        return settings;
    }
}
