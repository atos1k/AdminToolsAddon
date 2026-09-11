package dev.atos1k.auc.util;

import dev.by1337.auc.handler.Auction;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class NameResolver {

    private NameResolver() {
    }

    public static void resolve(Auction auction, UUID uuid, Map<UUID, String> cache, Consumer<String> then) {
        if (uuid == null) {
            then.accept(null);
            return;
        }
        String cached = cache.get(uuid);
        if (cached != null) {
            then.accept(cached);
            return;
        }
        auction.loadName(uuid).then(playerName -> {
            String name = playerName != null ? playerName.name() : uuid.toString();
            cache.put(uuid, name);
            then.accept(name);
        });
    }
}
