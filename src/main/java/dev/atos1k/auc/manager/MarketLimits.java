package dev.atos1k.auc.manager;

import dev.atos1k.auc.util.AddonConfig;
import dev.atos1k.auc.util.BYaml;
import dev.by1337.yaml.YamlMap;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class MarketLimits {
    public enum Kind {
        SELL, BUY
    }

    private final File file;
    private final Consumer<String> warn;
    private final Map<UUID, ArrayDeque<Long>> sells = new ConcurrentHashMap<>();
    private final Map<UUID, ArrayDeque<Long>> buys = new ConcurrentHashMap<>();
    private volatile int windowMinutes = 60;
    private volatile int sellLimit = 0;
    private volatile int buyLimit = 0;
    private volatile int dumpPercent = 40;

    public MarketLimits(File folder, Consumer<String> warn) {
        this.file = new File(folder, "limits.yml");
        this.warn = warn;
    }

    public void load() {
        YamlMap yaml = BYaml.read(file);
        windowMinutes = Math.max(1, yaml.get("window-minutes").asInt(AddonConfig.i("ratelimit.window-minutes", 60)));
        sellLimit = Math.max(0, yaml.get("sell").asInt(AddonConfig.i("ratelimit.sell", 0)));
        buyLimit = Math.max(0, yaml.get("buy").asInt(AddonConfig.i("ratelimit.buy", 0)));
        dumpPercent = Math.max(0, Math.min(95, yaml.get("dump-percent").asInt(AddonConfig.i("dump-alert.default-percent", 40))));
    }

    private synchronized void save() {
        YamlMap yaml = new YamlMap();
        yaml.set("window-minutes", windowMinutes);
        yaml.set("sell", sellLimit);
        yaml.set("buy", buyLimit);
        yaml.set("dump-percent", dumpPercent);
        try {
            BYaml.write(file, yaml);
        } catch (IOException e) {
            warn.accept("Не удалось сохранить limits.yml: " + e.getMessage());
        }
    }

    public int windowMinutes() {
        return windowMinutes;
    }

    public int limit(Kind kind) {
        return kind == Kind.SELL ? sellLimit : buyLimit;
    }

    public int dumpPercent() {
        return dumpPercent;
    }

    public void setWindowMinutes(int minutes) {
        windowMinutes = Math.max(1, minutes);
        save();
    }

    public void setLimit(Kind kind, int value) {
        if (kind == Kind.SELL) sellLimit = Math.max(0, value);
        else buyLimit = Math.max(0, value);
        save();
    }

    public void setDumpPercent(int percent) {
        dumpPercent = Math.max(0, Math.min(95, percent));
        save();
    }

    public long acquire(UUID who, Kind kind) {
        int limit = limit(kind);
        if (limit <= 0) return 0L;
        Map<UUID, ArrayDeque<Long>> map = kind == Kind.SELL ? sells : buys;
        ArrayDeque<Long> deque = map.computeIfAbsent(who, key -> new ArrayDeque<>());
        long windowMillis = windowMinutes * 60_000L;
        long now = System.currentTimeMillis();
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > windowMillis) deque.pollFirst();
            if (deque.size() >= limit) {
                return Math.max(1L, (deque.peekFirst() + windowMillis - now + 999L) / 1000L);
            }
            deque.addLast(now);
            return 0L;
        }
    }

    public void forget(UUID who) {
        sells.remove(who);
        buys.remove(who);
    }
}
