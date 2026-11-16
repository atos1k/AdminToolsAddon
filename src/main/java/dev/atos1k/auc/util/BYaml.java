package dev.atos1k.auc.util;

import dev.by1337.yaml.YamlMap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

public final class BYaml {
    private BYaml() {
    }

    public static YamlMap read(File file) {
        if (!file.exists()) return new YamlMap();
        try {
            YamlMap map = YamlMap.load(file);
            return map == null ? new YamlMap() : map;
        } catch (RuntimeException e) {
            return new YamlMap();
        }
    }

    public static void write(File file, YamlMap map) throws IOException {
        Files.writeString(file.toPath(), map.saveToString(), StandardCharsets.UTF_8);
    }

    public static Map<String, Object> section(YamlMap map, String path) {
        try {
            Object raw = map.getRaw(path);
            if (raw instanceof Map<?, ?> source) {
                Map<String, Object> out = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : source.entrySet()) out.put(String.valueOf(entry.getKey()), entry.getValue());
                return out;
            }
        } catch (IllegalStateException ignored) {
        }
        return Map.of();
    }

    public static long number(Object raw, long def) {
        return raw instanceof Number number ? number.longValue() : def;
    }
}
