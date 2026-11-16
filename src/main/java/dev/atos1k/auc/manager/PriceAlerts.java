package dev.atos1k.auc.manager;

import dev.atos1k.auc.util.AddonConfig;
import dev.atos1k.auc.util.BYaml;
import dev.by1337.yaml.YamlMap;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PriceAlerts {
    private final File file;
    private final Consumer<String> warn;
    private final Map<UUID, Map<String, Long>> alerts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>();

    public PriceAlerts(File folder, Consumer<String> warn) {
        this.file = new File(folder, "alerts.yml");
        this.warn = warn;
    }

    public static int maxPerPlayer() {
        return Math.max(1, AddonConfig.i("alert.max-per-player", 10));
    }

    public void load() {
        alerts.clear();
        YamlMap yaml = BYaml.read(file);
        for (Map.Entry<String, Object> entry : BYaml.section(yaml, "alerts").entrySet()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> materials)) continue;
            Map<String, Long> map = new ConcurrentHashMap<>();
            for (Map.Entry<?, ?> material : materials.entrySet()) {
                map.put(String.valueOf(material.getKey()).toUpperCase(Locale.ROOT), BYaml.number(material.getValue(), 0L));
            }
            if (!map.isEmpty()) alerts.put(uuid, map);
        }
    }

    private synchronized void save() {
        YamlMap yaml = new YamlMap();
        for (Map.Entry<UUID, Map<String, Long>> entry : alerts.entrySet()) {
            for (Map.Entry<String, Long> alert : entry.getValue().entrySet()) {
                yaml.set("alerts." + entry.getKey() + "." + alert.getKey(), alert.getValue());
            }
        }
        try {
            BYaml.write(file, yaml);
        } catch (IOException e) {
            warn.accept("Не удалось сохранить alerts.yml: " + e.getMessage());
        }
    }

    public boolean add(UUID who, String material, long maxUnitCents) {
        String key = material.toUpperCase(Locale.ROOT);
        Map<String, Long> map = alerts.computeIfAbsent(who, id -> new ConcurrentHashMap<>());
        if (!map.containsKey(key) && map.size() >= maxPerPlayer()) return false;
        map.put(key, maxUnitCents);
        save();
        return true;
    }

    public boolean remove(UUID who, String material) {
        Map<String, Long> map = alerts.get(who);
        if (map == null || map.remove(material.toUpperCase(Locale.ROOT)) == null) return false;
        if (map.isEmpty()) alerts.remove(who);
        save();
        return true;
    }

    public Map<String, Long> list(UUID who) {
        Map<String, Long> map = alerts.get(who);
        return map == null ? Map.of() : new TreeMap<>(map);
    }

    public List<UUID> matching(String material, long unitCents) {
        String key = material.toUpperCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0L, AddonConfig.l("alert.cooldown-seconds", 180L)) * 1000L;
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, Long>> entry : alerts.entrySet()) {
            Long target = entry.getValue().get(key);
            if (target == null || unitCents > target) continue;
            String stamp = entry.getKey() + ":" + key;
            Long previous = lastSent.get(stamp);
            if (previous != null && now - previous < cooldown) continue;
            lastSent.put(stamp, now);
            out.add(entry.getKey());
        }
        return out;
    }
}
