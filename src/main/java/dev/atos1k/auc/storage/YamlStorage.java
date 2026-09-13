package dev.atos1k.auc.storage;

import dev.atos1k.auc.util.BanTypes;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class YamlStorage implements Storage {
    private final File file;

    public YamlStorage(File folder) {
        if (!folder.exists()) folder.mkdirs();
        this.file = new File(folder, "admin-data.yml");
    }

    private YamlConfiguration read() {
        return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private void write(YamlConfiguration config) {
        try {
            config.save(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<BanEntry> loadBans() {
        List<BanEntry> out = new ArrayList<>();
        ConfigurationSection root = read().getConfigurationSection("bans");
        if (root == null) return out;
        for (String key : root.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection player = root.getConfigurationSection(key);
            if (player == null) continue;
            String name = player.getString("name");
            ConfigurationSection types = player.getConfigurationSection("types");
            if (types == null) continue;
            for (String typeId : types.getKeys(false)) {
                BanTypes type = BanTypes.byId(typeId);
                if (type == null) continue;
                ConfigurationSection entry = types.getConfigurationSection(typeId);
                if (entry == null) continue;
                out.add(new BanEntry(uuid, name, type,
                        entry.getLong("created"), entry.getLong("expires"), entry.getString("reason")));
            }
        }
        return out;
    }

    @Override
    public void putBan(BanEntry entry) {
        YamlConfiguration config = read();
        String path = "bans." + entry.uuid();
        config.set(path + ".name", entry.name());
        config.set(path + ".types." + entry.type().id() + ".created", entry.created());
        config.set(path + ".types." + entry.type().id() + ".expires", entry.expires());
        config.set(path + ".types." + entry.type().id() + ".reason", entry.reason());
        write(config);
    }

    @Override
    public void removeBan(UUID uuid, BanTypes type) {
        YamlConfiguration config = read();
        config.set("bans." + uuid + ".types." + type.id(), null);
        ConfigurationSection types = config.getConfigurationSection("bans." + uuid + ".types");
        if (types == null || types.getKeys(false).isEmpty()) {
            config.set("bans." + uuid, null);
        }
        write(config);
    }

    @Override
    public void removeBans(UUID uuid) {
        YamlConfiguration config = read();
        config.set("bans." + uuid, null);
        write(config);
    }

    @Override
    public List<String> loadBlacklist() {
        List<String> out = new ArrayList<>();
        for (String material : read().getStringList("blacklist")) {
            out.add(material.toUpperCase(Locale.ROOT));
        }
        return out;
    }

    @Override
    public void addBlacklist(String material, String addedBy) {
        YamlConfiguration config = read();
        List<String> list = config.getStringList("blacklist");
        if (!list.contains(material)) list.add(material);
        config.set("blacklist", list);
        write(config);
    }

    @Override
    public void removeBlacklist(String material) {
        YamlConfiguration config = read();
        List<String> list = config.getStringList("blacklist");
        list.remove(material);
        config.set("blacklist", list);
        write(config);
    }

    @Override
    public void close() {
    }
}
