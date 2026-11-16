package dev.atos1k.auc.util;

import dev.by1337.core.util.text.minimessage.BMM;
import dev.by1337.edsl.MessageManager;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.plc.PlaceholderResolver;
import dev.by1337.yaml.YamlMap;
import dev.by1337.yaml.YamlValue;
import dev.by1337.yaml.codec.DataResult;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;

public final class Lang {
    private static final String RESOURCE_PATH = "/messages.yml";
    private static final String COMPILED_PATH = ".cache/messages.yml";
    private static final String COMPILED_HEADER = "# Собрано из messages.yml для EventDSL, перезаписывается при каждой загрузке.\n";
    private static final Set<String> MAIN_THREAD_COMMANDS = Set.of("console", "player", "chat", "give_effect", "cooldown");

    private static Plugin plugin;
    private static File file;
    private static File compiled;
    private static String relativePath;
    private static volatile Map<String, Object> raw = Map.of();
    private static volatile Set<String> mainThreadKeys = Set.of();
    private static volatile MessageManager manager;

    private Lang() {
    }

    public static void load(File folder, Plugin owner) {
        if (!folder.exists()) {
            folder.mkdirs();
        }
        plugin = owner;
        file = new File(folder, "messages.yml");
        compiled = new File(folder, COMPILED_PATH);
        relativePath = owner.getDataFolder().toPath().toAbsolutePath().normalize()
                .relativize(compiled.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
        try {
            String defaults = defaults();
            if (file.exists()) {
                MessagesFile.sync(file, defaults, owner.getSLF4JLogger());
            } else {
                Files.writeString(file.toPath(), defaults, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            owner.getSLF4JLogger().error("Не удалось подготовить messages.yml", e);
        }
        reload();
    }

    public static void reload() {
        if (file == null || !file.exists()) return;
        Logger log = plugin.getSLF4JLogger();
        try {
            Map<String, Object> fallback = MessagesFile.flatten(MessagesFile.documents(defaults()).get(0));
            List<YamlMap> documents = MessagesFile.documents(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            if (documents.isEmpty()) documents = List.of(new YamlMap());
            Map<String, Object> primary = null;
            Set<String> sync = new HashSet<>();
            StringBuilder flat = new StringBuilder(COMPILED_HEADER);
            for (YamlMap document : documents) {
                Map<String, Object> merged = new LinkedHashMap<>(fallback);
                merged.putAll(MessagesFile.flatten(document));
                Map<String, Object> values = MessagesFile.resolve(merged);
                YamlMap stored = new YamlMap();
                Object lang = document.getRaw().get(MessagesFile.LANG_KEY);
                if (lang != null) stored.getRaw().put(MessagesFile.LANG_KEY, lang);
                values.forEach((key, value) -> {
                    stored.getRaw().put(storeKey(key), value);
                    if (needsMainThread(value)) sync.add(key);
                });
                if (primary == null) {
                    primary = values;
                } else {
                    flat.append("---\n");
                }
                flat.append(stored.saveToString());
            }
            Files.createDirectories(compiled.getParentFile().toPath());
            Files.writeString(compiled.toPath(), flat, StandardCharsets.UTF_8);
            raw = primary;
            mainThreadKeys = sync;
        } catch (IOException | RuntimeException e) {
            log.error("Не удалось прочитать messages.yml", e);
            return;
        }
        DataResult<MessageManager> result = MessageManager.decoder(relativePath, null).decode(YamlValue.wrap(Map.of()), plugin);
        MessageManager loaded = result.result();
        if (loaded == null) {
            log.error("Не удалось загрузить messages.yml: {}", result.errorOrDefault("неизвестная ошибка"));
            return;
        }
        if (result.hasError()) {
            log.warn("Замечания к messages.yml: {}", result.error());
        }
        manager = loaded;
    }

    public static void send(CommandSender to, String key, Object... placeholdersAndValues) {
        if (to instanceof Player player && manager != null && raw.containsKey(key)) {
            if (mainThreadKeys.contains(key) && !Bukkit.isPrimaryThread()) {
                Bukkit.getScheduler().runTask(plugin, () -> dispatch(player, key, placeholdersAndValues));
            } else {
                dispatch(player, key, placeholdersAndValues);
            }
            return;
        }
        for (String line : formattedLines(key, placeholdersAndValues)) {
            to.sendMessage(mini(line));
        }
    }

    public static void broadcast(String key, Object... placeholdersAndValues) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player, key, placeholdersAndValues);
        }
        send(Bukkit.getConsoleSender(), key, placeholdersAndValues);
    }

    public static String raw(String path) {
        Object value = raw.get(path);
        if (value == null) return path;
        List<String> lines = textLines(value);
        return lines.isEmpty() ? "" : lines.get(0);
    }

    public static List<String> rawList(String path) {
        return textLines(raw.get(path));
    }

    public static String rawFormatted(String path, Object... placeholdersAndValues) {
        return apply(raw(path), placeholdersAndValues);
    }

    public static Component get(String path) {
        return get(path, new Object[0]);
    }

    public static Component get(String path, Object... placeholdersAndValues) {
        List<String> lines = formattedLines(path, placeholdersAndValues);
        if (lines.size() == 1) return mini(lines.get(0));
        List<Component> parts = new ArrayList<>(lines.size());
        for (String line : lines) {
            parts.add(mini(line));
        }
        return Component.join(JoinConfiguration.newlines(), parts);
    }

    public static List<Component> getList(String path) {
        List<Component> out = new ArrayList<>();
        for (String line : rawList(path)) {
            out.add(mini(line));
        }
        return out;
    }

    private static void dispatch(Player player, String key, Object[] placeholdersAndValues) {
        MessageManager current = manager;
        try {
            current.call(storeKey(key), player, resolver(placeholdersAndValues));
        } catch (RuntimeException e) {
            plugin.getSLF4JLogger().warn("Сообщение {} не отправлено игроку {}: {}", key, player.getName(), e.getMessage());
            for (String line : formattedLines(key, placeholdersAndValues)) {
                player.sendMessage(mini(line));
            }
        }
    }

    private static List<String> formattedLines(String key, Object[] placeholdersAndValues) {
        Object value = raw.get(key);
        if (value == null) return List.of(key);
        List<String> out = new ArrayList<>();
        for (String line : textLines(value)) {
            out.add(apply(line, placeholdersAndValues));
        }
        return out;
    }

    private static List<String> textLines(Object value) {
        if (value instanceof String text) return List.of(text);
        if (!(value instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof String text) {
                if (!text.startsWith("[")) out.add(text);
            } else if (item instanceof Map<?, ?> map && map.get("message") instanceof String text) {
                out.add(text);
            }
        }
        return out;
    }

    private static boolean needsMainThread(Object value) {
        if (value instanceof String text) return isMainThreadCommand(text);
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String name && MAIN_THREAD_COMMANDS.contains(name.toLowerCase(Locale.ROOT))) {
                    return true;
                }
                if (needsMainThread(entry.getValue())) return true;
            }
            return false;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (needsMainThread(item)) return true;
            }
        }
        return false;
    }

    private static boolean isMainThreadCommand(String text) {
        int end = text.indexOf(']');
        return text.startsWith("[") && end > 0
                && MAIN_THREAD_COMMANDS.contains(text.substring(1, end).toLowerCase(Locale.ROOT));
    }

    private static String storeKey(String key) {
        return key.replace('.', '_');
    }

    private static PlaceholderResolver<EventContext> resolver(Object[] placeholdersAndValues) {
        PlaceholderResolver<EventContext> resolver = null;
        for (int i = 0; i + 1 < placeholdersAndValues.length; i += 2) {
            String name = String.valueOf(placeholdersAndValues[i]);
            String value = String.valueOf(placeholdersAndValues[i + 1]);
            resolver = resolver == null ? PlaceholderResolver.<EventContext>of(name, value) : resolver.append(name, value);
        }
        return resolver == null ? PlaceholderResolver.<EventContext>of("_", "") : resolver;
    }

    private static String apply(String text, Object[] placeholdersAndValues) {
        if (placeholdersAndValues.length < 2 || text.indexOf('{') < 0) return text;
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i + 1 < placeholdersAndValues.length; i += 2) {
            values.put(String.valueOf(placeholdersAndValues[i]), String.valueOf(placeholdersAndValues[i + 1]));
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        int length = text.length();
        int i = 0;
        while (i < length) {
            char c = text.charAt(i);
            if (c == '{') {
                int end = text.indexOf('}', i + 1);
                if (end > 0) {
                    String value = values.get(text.substring(i + 1, end));
                    if (value != null) {
                        out.append(value);
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String defaults() throws IOException {
        try (InputStream in = Lang.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) throw new IOException("В jar нет " + RESOURCE_PATH);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Component mini(String s) {
        return BMM.deserialize(s);
    }
}
