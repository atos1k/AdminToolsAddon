package dev.atos1k.auc.util;

import dev.by1337.core.util.text.minimessage.BMM;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Lang {
    private static final String RESOURCE_PATH = "/messages.yml";
    private static YamlConfiguration config;
    private static File file;
    private Lang() {
    }

    public static void load(File folder) {
        if (!folder.exists()) {
            folder.mkdirs();
        }
        file = new File(folder, "messages.yml");
        if (!file.exists()) {
            try (InputStream in = Lang.class.getResourceAsStream(RESOURCE_PATH)) {
                if (in != null) {
                    Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        reload();
    }

    public static void reload() {
        if (file == null || !file.exists()) return;
        config = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = Lang.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
                config.options().copyDefaults(true);
                if (file.exists()) {
                    try {
                        config.save(file);
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static String raw(String path) {
        if (config == null) return path;
        String value = config.getString(path);
        return value != null ? value : path;
    }

    public static List<String> rawList(String path) {
        if (config == null) return List.of();
        return config.getStringList(path);
    }

    public static String rawFormatted(String path, Object... placeholdersAndValues) {
        String s = raw(path);
        for (int i = 0; i + 1 < placeholdersAndValues.length; i += 2) {
            s = s.replace("%" + placeholdersAndValues[i] + "%", String.valueOf(placeholdersAndValues[i + 1]));
        }
        return s;
    }

    public static Component get(String path) {
        return mini(raw(path));
    }
    
    public static Component get(String path, Object... placeholdersAndValues) {
        return mini(rawFormatted(path, placeholdersAndValues));
    }

    public static List<Component> getList(String path) {
        return rawList(path).stream().map(Lang::mini).toList();
    }

    private static Component mini(String s) {
        return BMM.deserialize(s);
    }
}

