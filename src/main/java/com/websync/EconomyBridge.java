package com.websync;

import org.bukkit.entity.Player;

/**
 * WebSync doesn't assume any particular economy plugin — servers
 * run everything from Vault-backed economies to fully custom ones.
 *
 * By default {@link #getBalance(Player)} returns 0. If this server has
 * a real economy, register a bridge from another plugin (or a small
 * addition to this one) at startup:
 *
 * <pre>{@code
 * EconomyBridge.register(player -> myEconomyPlugin.getBalance(player));
 * }</pre>
 *
 * This indirection exists so WebSync never has to guess at (and
 * potentially get wrong) a specific economy plugin's API.
 */
public final class EconomyBridge {

    @FunctionalInterface
    public interface Provider {
        long getBalance(Player player);
    }

    private static volatile Provider provider = player -> 0L;

    private EconomyBridge() {
    }

    public static void register(Provider newProvider) {
        provider = newProvider != null ? newProvider : (player -> 0L);
    }

    public static long getBalance(Player player) {
        try {
            return Math.max(0L, provider.getBalance(player));
        } catch (Exception e) {
            // A misbehaving external provider must never break syncing.
            return 0L;
        }
    }
}
