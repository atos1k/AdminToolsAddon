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
        sender.sendMessage(Messages.deny());
        return true;
    }

    private ItemStack heldItem(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("blacklist.not-player"));
            return null;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            sender.sendMessage(Lang.get("blacklist.empty-hand"));
            return null;
        }
        return item;
    }

    public void handleAdd(CommandSender sender, Material material) {
        if (denied(sender)) return;
        if (material == null) {
            sender.sendMessage(Lang.get("usage.blacklist-add"));
            return;
        }
        if (!addon.blacklist().addMaterial(material, sender.getName())) {
            sender.sendMessage(Lang.get("blacklist.already", "value", material.name()));
            return;
        }
        sender.sendMessage(Lang.get("blacklist.added-material", "material", material.name()));
    }

    public void handleHand(CommandSender sender) {
        if (denied(sender)) return;
        ItemStack item = heldItem(sender);
        if (item == null) return;
        Material material = item.getType();
        if (!addon.blacklist().addMaterial(material, sender.getName())) {
            sender.sendMessage(Lang.get("blacklist.already", "value", material.name()));
            return;
        }
        sender.sendMessage(Lang.get("blacklist.added-material", "material", material.name()));
    }

    public void handleTag(CommandSender sender, String tag) {
        if (denied(sender)) return;
        if (tag == null || tag.isBlank()) {
            sender.sendMessage(Lang.get("usage.blacklist-tag"));
            return;
        }
        if (!addon.blacklist().addTag(tag, sender.getName())) {
            sender.sendMessage(Lang.get("blacklist.already", "value", tag));
            return;
        }
        sender.sendMessage(Lang.get("blacklist.added-tag", "tag", tag));
    }

    public void handleHandTags(CommandSender sender) {
        if (denied(sender)) return;
        ItemStack item = heldItem(sender);
        if (item == null) return;
        Set<String> tags = addon.blacklist().extractTags(item);
        if (tags.isEmpty()) {
            sender.sendMessage(Lang.get("blacklist.no-tags"));
            return;
        }
        sender.sendMessage(Messages.header(Lang.rawFormatted("blacklist.tags-header",
                "material", item.getType().name(), "count", tags.size())));
        for (String tag : tags) {
            sender.sendMessage(Lang.get("blacklist.tag-line", "tag", tag,
                    "listed", addon.blacklist().containsTag(tag) ? Lang.raw("blacklist.tag-listed") : ""));
        }
    }

    public void handleRemove(CommandSender sender, String value) {
        if (denied(sender)) return;
        if (value == null || value.isBlank()) {
            sender.sendMessage(Lang.get("usage.blacklist-remove"));
            return;
        }
        Material material = Material.matchMaterial(value);
        if (material != null && addon.blacklist().removeMaterial(material)) {
            sender.sendMessage(Lang.get("blacklist.removed", "value", material.name()));
            return;
        }
        if (addon.blacklist().removeTag(value)) {
            sender.sendMessage(Lang.get("blacklist.removed", "value", value));
            return;
        }
        sender.sendMessage(Lang.get("blacklist.not-listed", "value", value));
    }

    public void handleList(CommandSender sender) {
        if (denied(sender)) return;
        List<Material> materials = addon.blacklist().allMaterials();
        List<String> tags = addon.blacklist().allTags();
        if (materials.isEmpty() && tags.isEmpty()) {
            sender.sendMessage(Lang.get("blacklist.empty"));
            return;
        }
        sender.sendMessage(Messages.header(Lang.rawFormatted("blacklist.header",
                "count", materials.size() + tags.size())));
        if (!materials.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Material material : materials) {
                names.add(material.name());
            }
            sender.sendMessage(Lang.get("blacklist.materials-line", "materials", String.join(", ", names)));
        }
        if (!tags.isEmpty()) {
            sender.sendMessage(Lang.get("blacklist.tags-line", "tags", String.join(", ", tags)));
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
