package com.websync;

import com.websync.json.JsonWriter;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.Set;

/**
 * Builds one player's JSON object exactly matching the website's
 * {@code websyncPlayerSchema} (see {@code src/lib/websync/payloads.ts}).
 *
 * Stat sourcing:
 *  - kills / deaths / mobKills / blocksBroken: read directly from
 *    Bukkit's own persisted {@link Statistic} data — already
 *    cumulative and survives restarts, so no local tracking needed.
 *  - blocksPlaced: this server's own persisted counter (see
 *    {@link PlacedBlocksStore}) — vanilla has no equivalent built-in stat.
 *  - balance: delegated to {@link EconomyBridge} (0 unless a bridge is
 *    registered) since economy plugins vary per server.
 *  - experience: {@link Player#getTotalExperience()} — an
 *    approximation; vanilla experience isn't a single monotonic
 *    counter the way statistics are (it changes on level up/death).
 */
public final class PlayerPayloadFactory {

    /** Cached once: iterating ~1000 Material values on every sync would be wasteful. */
    private static final Set<Material> BLOCK_MATERIALS;

    static {
        EnumSet<Material> blocks = EnumSet.noneOf(Material.class);
        for (Material material : Material.values()) {
            if (material.isBlock() && material.isItem() && !material.isLegacy()) {
                blocks.add(material);
            }
        }
        BLOCK_MATERIALS = blocks;
    }

    private final PlacedBlocksStore placedBlocksStore;

    public PlayerPayloadFactory(PlacedBlocksStore placedBlocksStore) {
        this.placedBlocksStore = placedBlocksStore;
    }

    /**
     * @param platform      "JAVA" or "BEDROCK" — resolved by the caller (e.g. via
     *                      Floodgate's API), since Bukkit alone can't tell.
     * @param playtimeMinutes authoritative cumulative playtime, resolved by the caller.
     * @param rankSlug      resolved by the caller (e.g. from a permissions/rank
     *                      plugin), or {@code null} to leave rank untouched on sync.
     * @param online        whether this snapshot represents a connected player.
     *                      Passed in explicitly rather than read from
     *                      {@link Player#isOnline()}: during
     *                      {@code PlayerQuitEvent} the quitting player has not
     *                      been removed from the server's player list yet, so
     *                      {@code isOnline()} still returns {@code true} and a
     *                      quit snapshot would report the player as online —
     *                      leaving them permanently "online" on the website.
     * @param includeStats  whether to include the stats sub-object at all.
     */
    public Object build(
            Player player, String platform, int playtimeMinutes, String rankSlug,
            boolean online, boolean includeStats) {
        Object[] fields = new Object[]{
                "uuid", player.getUniqueId().toString(),
                "username", player.getName(),
                "platform", platform,
                "isOnline", online,
                "playtimeMinutes", playtimeMinutes,
        };

        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < fields.length; i += 2) {
            map.put((String) fields[i], fields[i + 1]);
        }
        if (rankSlug != null) {
            map.put("rankSlug", rankSlug);
        }
        if (includeStats) {
            map.put("stats", buildStats(player));
        }
        return map;
    }

    private Object buildStats(Player player) {
        int blocksBroken = 0;
        for (Material material : BLOCK_MATERIALS) {
            blocksBroken += player.getStatistic(Statistic.MINE_BLOCK, material);
        }

        return JsonWriter.obj(
                "kills", player.getStatistic(Statistic.PLAYER_KILLS),
                "deaths", player.getStatistic(Statistic.DEATHS),
                "mobKills", player.getStatistic(Statistic.MOB_KILLS),
                "blocksBroken", blocksBroken,
                "blocksPlaced", placedBlocksStore.get(player.getUniqueId()),
                "balance", clampBalanceToInt(EconomyBridge.getBalance(player)),
                "experience", Math.max(0, player.getTotalExperience())
        );
    }

    /**
     * EconomyBridge reports balances as {@code long} (economy plugins can
     * use doubles/longs internally), but the website's
     * {@code PlayerStats.balance} column is a 32-bit {@code Int}. A naive
     * {@code (int)} cast on a value above {@link Integer#MAX_VALUE} would
     * silently wrap around into a negative number — clamp instead so an
     * extreme in-game balance just caps out rather than corrupting data.
     */
    private static int clampBalanceToInt(long balance) {
        if (balance > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (balance < 0) {
            // EconomyBridge.getBalance() already floors at 0, but stay
            // defensive here too since this value feeds a public leaderboard.
            return 0;
        }
        return (int) balance;
    }
}
