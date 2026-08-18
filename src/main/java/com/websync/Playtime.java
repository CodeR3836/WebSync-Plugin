package com.websync;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;

/**
 * {@code Statistic.PLAY_ONE_MINUTE} is a misleadingly-named but
 * genuinely cumulative, server-persisted "total ticks played" counter
 * (survives restarts) — exactly the authoritative value WebSync needs,
 * with no local tracking required.
 */
public final class Playtime {

    private static final int TICKS_PER_MINUTE = 20 * 60;

    private Playtime() {
    }

    public static int minutes(Player player) {
        int ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        return Math.max(0, ticks / TICKS_PER_MINUTE);
    }
}
