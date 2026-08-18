package com.websync;

import org.bukkit.entity.Player;

/**
 * Resolves whether a player is on {@code JAVA} or {@code BEDROCK}, so
 * WebSync can report it correctly. Bukkit/Paper alone can't tell —
 * that normally comes from a Bedrock-compatibility layer like Geyser/
 * Floodgate. Left pluggable rather than hard-depending on one specific
 * plugin's API/version.
 *
 * Default: every player is reported as {@code JAVA}. If this server
 * runs Geyser/Floodgate (or another Bedrock bridge), register a real
 * provider at startup:
 *
 * <pre>{@code
 * PlatformBridge.register(player ->
 *         FloodgateApi.getInstance().isFloodgateId(player.getUniqueId())
 *                 ? "BEDROCK" : "JAVA");
 * }</pre>
 */
public final class PlatformBridge {

    @FunctionalInterface
    public interface Provider {
        /** Must return exactly {@code "JAVA"} or {@code "BEDROCK"}. */
        String getPlatform(Player player);
    }

    private static volatile Provider provider = player -> "JAVA";

    private PlatformBridge() {
    }

    public static void register(Provider newProvider) {
        provider = newProvider != null ? newProvider : (player -> "JAVA");
    }

    public static String getPlatform(Player player) {
        try {
            String platform = provider.getPlatform(player);
            return "BEDROCK".equals(platform) ? "BEDROCK" : "JAVA";
        } catch (Exception e) {
            return "JAVA";
        }
    }
}
