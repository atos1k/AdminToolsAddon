package dev.atos1k.auc.storage;

import dev.atos1k.auc.util.BanTypes;
import java.util.List;
import java.util.UUID;

public interface Storage {
    List<BanEntry> loadBans() throws Exception;

    void putBan(BanEntry entry) throws Exception;

    void removeBan(UUID uuid, BanTypes type) throws Exception;

    void removeBans(UUID uuid) throws Exception;

    List<String> loadBlacklist() throws Exception;

    void addBlacklist(String material, String addedBy) throws Exception;

    void removeBlacklist(String material) throws Exception;

    void close();
}
