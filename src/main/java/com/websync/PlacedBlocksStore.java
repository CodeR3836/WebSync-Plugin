package com.websync;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracks a cumulative "blocks placed" count per player.
 *
 * Unlike {@code Statistic.MINE_BLOCK} (blocks broken), vanilla
 * Minecraft has no equivalent built-in, per-block-type "placed"
 * statistic the server persists for us — so this plugin tracks its own
 * running total and persists it to {@code placed-blocks.yml}. Without
 * this, a server restart would reset the count to 0 and the next sync
 * would overwrite the website's (correct, higher) historical value —
 * since the website treats whatever the plugin reports as authoritative.
 */
public final class PlacedBlocksStore {

    private final Logger logger;
    private final File file;
    private final ConcurrentHashMap<UUID, AtomicInteger> counts = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public PlacedBlocksStore(Logger logger, File dataFolder) {
        this.logger = logger;
        this.file = new File(dataFolder, "placed-blocks.yml");
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                counts.put(UUID.fromString(key), new AtomicInteger(yaml.getInt(key, 0)));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed/foreign keys rather than failing plugin startup.
            }
        }
    }

    public void save() {
        if (!dirty) return;
        YamlConfiguration yaml = new YamlConfiguration();
        counts.forEach((uuid, count) -> yaml.set(uuid.toString(), count.get()));
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            logger.log(Level.WARNING, "[WebSync] Failed to save placed-blocks.yml", e);
        }
    }

    public void increment(UUID uuid) {
        counts.computeIfAbsent(uuid, k -> new AtomicInteger(0)).incrementAndGet();
        dirty = true;
    }

    public int get(UUID uuid) {
        AtomicInteger count = counts.get(uuid);
        return count != null ? count.get() : 0;
    }
}
