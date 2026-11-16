package dev.atos1k.auc.handler;

import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class BlacklistHandler {
    private final AdminToolsAddon addon;
    public BlacklistHandler(AdminToolsAddon addon) {
        this.addon = addon;
    }

    private boolean denied(CommandSender sender) {
        if (sender.hasPermission(Permissions.BLACKLIST)) return false;
        Lang.send(sender, "general.no-permission");
        return true;
    }

    private ItemStack heldItem(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Lang.send(sender, "blacklist.not-player");
            return null;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            Lang.send(sender, "blacklist.empty-hand");
            return null;
        }
        return item;
    }

    public void handleAdd(CommandSender sender, Material material) {
        if (denied(sender)) return;
        if (material == null) {
            Lang.send(sender, "usage.blacklist-add");
            return;
        }
        if (!addon.blacklist().addMaterial(material, sender.getName())) {
            Lang.send(sender, "blacklist.already", "value", material.name());
            return;
        }
        Lang.send(sender, "blacklist.added-material", "material", material.name());
    }

    public void handleHand(CommandSender sender) {
        if (denied(sender)) return;
        ItemStack item = heldItem(sender);
        if (item == null) return;
        Material material = item.getType();
        if (!addon.blacklist().addMaterial(material, sender.getName())) {
            Lang.send(sender, "blacklist.already", "value", material.name());
            return;
        }
        Lang.send(sender, "blacklist.added-material", "material", material.name());
    }

    public void handleTag(CommandSender sender, String tag) {
        if (denied(sender)) return;
        if (tag == null || tag.isBlank()) {
            Lang.send(sender, "usage.blacklist-tag");
            return;
        }
        if (!addon.blacklist().addTag(tag, sender.getName())) {
            Lang.send(sender, "blacklist.already", "value", tag);
            return;
        }
        Lang.send(sender, "blacklist.added-tag", "tag", tag);
    }

    public void handleHandTags(CommandSender sender) {
        if (denied(sender)) return;
        ItemStack item = heldItem(sender);
        if (item == null) return;
        Set<String> tags = addon.blacklist().extractTags(item);
        if (tags.isEmpty()) {
            Lang.send(sender, "blacklist.no-tags");
            return;
        }
        sender.sendMessage(Messages.header(Lang.rawFormatted("blacklist.tags-header",
                "material", item.getType().name(), "count", tags.size())));
        for (String tag : tags) {
            Lang.send(sender, "blacklist.tag-line", "tag", tag,
                    "listed", addon.blacklist().containsTag(tag) ? Lang.raw("blacklist.tag-listed") : "");
        }
    }

    public void handleRemove(CommandSender sender, String value) {
        if (denied(sender)) return;
        if (value == null || value.isBlank()) {
            Lang.send(sender, "usage.blacklist-remove");
            return;
        }
        Material material = Material.matchMaterial(value);
        if (material != null && addon.blacklist().removeMaterial(material)) {
            Lang.send(sender, "blacklist.removed", "value", material.name());
            return;
        }
        if (addon.blacklist().removeTag(value)) {
            Lang.send(sender, "blacklist.removed", "value", value);
            return;
        }
        Lang.send(sender, "blacklist.not-listed", "value", value);
    }

    public void handleList(CommandSender sender) {
        if (denied(sender)) return;
        List<Material> materials = addon.blacklist().allMaterials();
        List<String> tags = addon.blacklist().allTags();
        if (materials.isEmpty() && tags.isEmpty()) {
            Lang.send(sender, "blacklist.empty");
            return;
        }
        sender.sendMessage(Messages.header(Lang.rawFormatted("blacklist.header",
                "count", materials.size() + tags.size())));
        if (!materials.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Material material : materials) {
                names.add(material.name());
            }
            Lang.send(sender, "blacklist.materials-line", "materials", String.join(", ", names));
        }
        if (!tags.isEmpty()) {
            Lang.send(sender, "blacklist.tags-line", "tags", String.join(", ", tags));
        }
    }

    public List<String> listedValues() {
        List<String> out = new ArrayList<>();
        for (Material material : addon.blacklist().allMaterials()) {
            out.add(material.name());
        }
        out.addAll(addon.blacklist().allTags());
        return out;
    }
}
