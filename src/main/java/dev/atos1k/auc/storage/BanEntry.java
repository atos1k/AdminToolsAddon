package dev.atos1k.auc.storage;

import dev.atos1k.auc.util.BanTypes;
import java.util.UUID;

public record BanEntry(UUID uuid, String name, BanTypes type, long created, long expires, String reason) {
    public boolean expired() {
        return expires > 0 && expires <= System.currentTimeMillis();
    }

    public boolean permanent() {
        return expires <= 0;
    }
}
