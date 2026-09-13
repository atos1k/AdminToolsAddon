package dev.atos1k.auc.manager;

import dev.atos1k.auc.storage.BanEntry;
import dev.atos1k.auc.storage.Storage;
import dev.atos1k.auc.util.BanTypes;
import dev.by1337.auc.handler.SimpleAuction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class BanManager {
    private final Map<UUID, Map<BanTypes, BanEntry>> bans = new ConcurrentHashMap<>();
    private final Storage storage;

    public BanManager(Storage storage) {
        this.storage = storage;
    }

    public void load(Consumer<String> logger) {
        bans.clear();
        try {
            for (BanEntry entry : storage.loadBans()) {
                if (entry.expired()) {
                    async(() -> storage.removeBan(entry.uuid(), entry.type()));
                    continue;
                }
                bans.computeIfAbsent(entry.uuid(), k -> new ConcurrentHashMap<>()).put(entry.type(), entry);
            }
        } catch (Exception e) {
            logger.accept("Не удалось загрузить блокировки: " + e);
        }
    }

    public BanEntry active(UUID uuid, BanTypes type) {
        if (uuid == null) return null;
        Map<BanTypes, BanEntry> types = bans.get(uuid);
        if (types == null) return null;
        BanEntry all = check(uuid, types, BanTypes.ALL);
        if (all != null) return all;
        return check(uuid, types, type);
    }

    private BanEntry check(UUID uuid, Map<BanTypes, BanEntry> types, BanTypes type) {
        BanEntry entry = types.get(type);
        if (entry == null) return null;
        if (entry.expired()) {
            types.remove(type);
            if (types.isEmpty()) bans.remove(uuid);
            async(() -> storage.removeBan(uuid, type));
            return null;
        }
        return entry;
    }

    public boolean isBanned(UUID uuid, BanTypes type) {
        return active(uuid, type) != null;
    }

    public boolean isBannedCompletely(UUID uuid) {
        return active(uuid, BanTypes.ALL) != null;
    }

    public List<BanEntry> entries(UUID uuid) {
        Map<BanTypes, BanEntry> types = bans.get(uuid);
        if (types == null) return List.of();
        List<BanEntry> out = new ArrayList<>();
        for (BanTypes type : types.keySet()) {
            BanEntry entry = check(uuid, types, type);
            if (entry != null) out.add(entry);
        }
        return Collections.unmodifiableList(out);
    }

    public BanEntry ban(UUID uuid, String name, BanTypes type, long duration, String reason) {
        long now = System.currentTimeMillis();
        BanEntry entry = new BanEntry(uuid, name, type, now, duration > 0 ? now + duration : 0, reason);
        Map<BanTypes, BanEntry> types = bans.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        List<BanTypes> replaced = new ArrayList<>();
        if (type == BanTypes.ALL) {
            for (BanTypes old : types.keySet()) {
                if (old != BanTypes.ALL) replaced.add(old);
            }
            types.clear();
        }
        types.put(type, entry);
        async(() -> {
            for (BanTypes old : replaced) {
                storage.removeBan(uuid, old);
            }
            storage.putBan(entry);
        });
        return entry;
    }

    public boolean unban(UUID uuid, BanTypes type) {
        Map<BanTypes, BanEntry> types = bans.get(uuid);
        if (types == null || types.isEmpty()) return false;
        if (type == null || type == BanTypes.ALL) {
            bans.remove(uuid);
            async(() -> storage.removeBans(uuid));
            return true;
        }
        if (types.remove(type) == null) return false;
        if (types.isEmpty()) bans.remove(uuid);
        async(() -> storage.removeBan(uuid, type));
        return true;
    }

    public Map<UUID, List<BanEntry>> all() {
        Map<UUID, List<BanEntry>> out = new HashMap<>();
        for (UUID uuid : bans.keySet()) {
            List<BanEntry> entries = entries(uuid);
            if (!entries.isEmpty()) out.put(uuid, entries);
        }
        return out;
    }

    public boolean isEmpty() {
        return bans.isEmpty();
    }

    private void async(ThrowingRunnable task) {
        SimpleAuction.WORKER.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
