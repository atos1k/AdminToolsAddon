package dev.atos1k.auc.util;

import dev.by1337.yaml.YamlMap;
import dev.by1337.yaml.YamlValue;
import dev.by1337.yaml.codec.DataResult;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class AddonConfig {
    private static final String RESOURCE_PATH = "/config.yml";
    private static volatile YamlMap live = new YamlMap();
    private static volatile YamlMap defaults = new YamlMap();

    private AddonConfig() {
    }

    public static void load(File folder) {
        if (!folder.exists()) folder.mkdirs();
        File file = new File(folder, "config.yml");
        if (!file.exists()) copyResource(file);
        copyResource(new File(folder, "config-reference.yml"));
        defaults = readResource();
        live = BYaml.read(file);
    }

    private static YamlMap readResource() {
        try (InputStream in = AddonConfig.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) return new YamlMap();
            YamlMap map = YamlMap.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return map == null ? new YamlMap() : map;
        } catch (IOException | RuntimeException e) {
            return new YamlMap();
        }
    }

    private static void copyResource(File target) {
        try (InputStream in = AddonConfig.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in != null) Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static YamlValue lookup(YamlMap map, String path) {
        try {
            return map.get(path);
        } catch (IllegalStateException e) {
            return YamlValue.EMPTY;
        }
    }

    private static YamlValue value(String path) {
        YamlValue value = lookup(live, path);
        return value.isNull() ? lookup(defaults, path) : value;
    }

    public static int i(String path, int def) {
        return value(path).asInt(def);
    }

    public static long l(String path, long def) {
        return value(path).asLong(def);
    }

    public static double d(String path, double def) {
        return value(path).asDouble(def);
    }

    public static boolean b(String path, boolean def) {
        return value(path).asBool(def);
    }

    public static String s(String path, String def) {
        return value(path).asString(def);
    }

    public static List<String> list(String path, List<String> def) {
        DataResult<List<String>> result = value(path).getAsStringList();
        return result.hasResult() && !result.result().isEmpty() ? result.result() : def;
    }

    public static int limit(String section, Integer arg, int def, int max) {
        int fallback = i(section + ".default-limit", def);
        int ceiling = Math.max(1, i(section + ".max-limit", max));
        return CommandUtil.clamp(arg == null ? fallback : arg, 1, ceiling);
    }

    public static int hours(String section, Integer arg, int def, int max) {
        int fallback = i(section + ".default-hours", def);
        int ceiling = Math.max(1, i(section + ".max-hours", max));
        return CommandUtil.clamp(arg == null ? fallback : arg, 1, ceiling);
    }

    public static int days(String section, Integer arg, int def, int max) {
        int fallback = i(section + ".default-days", def);
        int ceiling = Math.max(1, i(section + ".max-days", max));
        return CommandUtil.clamp(arg == null ? fallback : arg, 1, ceiling);
    }

    public static int scanLimit(String section, int def) {
        return Math.max(10, i(section + ".scan-limit", def));
    }
}
