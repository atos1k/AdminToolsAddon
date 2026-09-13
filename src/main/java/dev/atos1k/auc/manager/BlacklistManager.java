package dev.atos1k.auc.manager;

import dev.atos1k.auc.storage.Storage;
import dev.by1337.auc.BAuction;
import dev.by1337.auc.handler.SimpleAuction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class BlacklistManager {
    private static final String TAG_PREFIX = "tag:";
    private static final String MATERIAL_PREFIX = "material:";
    private final Set<Material> materials = ConcurrentHashMap.newKeySet();
    private final Set<String> tags = ConcurrentHashMap.newKeySet();
    private final Storage storage;

    public BlacklistManager(Storage storage) {
        this.storage = storage;
    }

    public void load(Consumer<String> logger) {
        materials.clear();
        tags.clear();
        try {
            for (String raw : storage.loadBlacklist()) {
                if (raw.startsWith(TAG_PREFIX)) {
                    tags.add(raw.substring(TAG_PREFIX.length()).toLowerCase(Locale.ROOT));
                    continue;
                }
                String name = raw.startsWith(MATERIAL_PREFIX) ? raw.substring(MATERIAL_PREFIX.length()) : raw;
                Material material = Material.matchMaterial(name);
                if (material != null) materials.add(material);
            }
        } catch (Exception e) {
            logger.accept("Не удалось загрузить чёрный список: " + e);
        }
    }

    public boolean containsMaterial(Material material) {
        return material != null && materials.contains(material);
    }

    public boolean containsTag(String tag) {
        return tag != null && tags.contains(tag.toLowerCase(Locale.ROOT));
    }

    public String findMatch(ItemStack item) {
        if (item == null) return null;
        if (materials.contains(item.getType())) return item.getType().name();
        if (tags.isEmpty()) return null;
        for (String tag : extractTags(item)) {
            if (tags.contains(tag.toLowerCase(Locale.ROOT))) return tag;
        }
        return null;
    }

    public Set<String> extractTags(ItemStack item) {
        try {
            return BAuction.plugin().config().tagsExtractor.extractTags(item);
        } catch (Throwable e) {
            return Set.of();
        }
    }

    public boolean addMaterial(Material material, String addedBy) {
        if (!materials.add(material)) return false;
        async(() -> storage.addBlacklist(MATERIAL_PREFIX + material.name(), addedBy));
        return true;
    }

    public boolean removeMaterial(Material material) {
        if (!materials.remove(material)) return false;
        async(() -> {
            storage.removeBlacklist(MATERIAL_PREFIX + material.name());
            storage.removeBlacklist(material.name());
        });
        return true;
    }

    public boolean addTag(String tag, String addedBy) {
        String normalized = tag.toLowerCase(Locale.ROOT);
        if (!tags.add(normalized)) return false;
        async(() -> storage.addBlacklist(TAG_PREFIX + normalized, addedBy));
        return true;
    }

    public boolean removeTag(String tag) {
        String normalized = tag.toLowerCase(Locale.ROOT);
        if (!tags.remove(normalized)) return false;
        async(() -> storage.removeBlacklist(TAG_PREFIX + normalized));
        return true;
    }

    public List<Material> allMaterials() {
        List<Material> out = new ArrayList<>(materials);
        out.sort((a, b) -> a.name().compareTo(b.name()));
        return out;
    }

    public List<String> allTags() {
        List<String> out = new ArrayList<>(tags);
        out.sort(String::compareTo);
        return out;
    }

    public boolean isEmpty() {
        return materials.isEmpty() && tags.isEmpty();
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
