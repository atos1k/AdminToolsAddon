package dev.atos1k.auc.util;

import java.util.UUID;

public record WatchFilter(UUID player, String playerName, String type, Long minCents) {
    public static final WatchFilter NONE = new WatchFilter(null, null, null, null);

    public boolean matches(UUID actor, UUID subject, String logType, long priceCents) {
        if (player != null && !player.equals(actor) && !player.equals(subject)) return false;
        if (type != null && !type.equals(logType)) return false;
        return minCents == null || priceCents >= minCents;
    }

    public boolean isEmpty() {
        return player == null && type == null && minCents == null;
    }

    public WatchFilter withPlayer(UUID uuid, String name) {
        return new WatchFilter(uuid, name, type, minCents);
    }

    public WatchFilter withType(String type) {
        return new WatchFilter(player, playerName, type, minCents);
    }

    public WatchFilter withMin(Long minCents) {
        return new WatchFilter(player, playerName, type, minCents);
    }
}
