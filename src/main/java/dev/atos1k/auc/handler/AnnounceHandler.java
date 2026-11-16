package dev.atos1k.auc.handler;

import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public class AnnounceHandler {
    public void handleAnnounce(CommandSender sender, String text) {
        if (!sender.hasPermission(Permissions.ANNOUNCE)) {
            Lang.send(sender, "general.no-permission");
            return;
        }
        if (text == null || text.isBlank()) {
            Lang.send(sender, "usage.announce");
            return;
        }
        Lang.broadcast("announce.prefix", "text", text);
        Lang.send(sender, "announce.sent", "count", Bukkit.getOnlinePlayers().size());
    }
}
