package com.websync;

import org.bukkit.entity.Player;

/**
 * Resolves the rank slug WebSync should report for a player, so it can
 * be matched against an existing {@code Rank.slug} on the website. Left
 * pluggable rather than hard-coded to one permissions/ranks plugin
 * (LuckPerms, GroupManager, a custom system, ...).
 *
 * Default: returns {@code null} for every player, meaning "don't touch
 * this player's rank" (per the website's documented rank-sync
 * behavior — see PHASE 4B spec, "RANK SYNC"). Register a real bridge
 * at startup to enable rank sync:
 *
 * <pre>{@code
 * RankBridge.register(player -> luckPerms.getPlayerAdapter(Player.class)
 *         .getMetaData(player).getPrimaryGroup());
 * }</pre>
 */
public final class RankBridge {

    @FunctionalInterface
    public interface Provider {
        /** Return the rank slug to report, or {@code null} to leave the
         *  player's current rank untouched on this sync. */
        String getRankSlug(Player player);
    }

    private static volatile Provider provider = player -> null;

    private RankBridge() {
    }

    public static void register(Provider newProvider) {
        provider = newProvider != null ? newProvider : (player -> null);
    }

    public static String getRankSlug(Player player) {
        try {
            return provider.getRankSlug(player);
        } catch (Exception e) {
            return null;
        }
    }
}
