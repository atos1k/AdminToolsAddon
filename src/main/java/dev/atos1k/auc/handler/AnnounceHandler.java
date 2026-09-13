package dev.atos1k.auc.handler;

import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AnnounceHandler {
    public void handleAnnounce(CommandSender sender, String text) {
        if (!sender.hasPermission(Permissions.ANNOUNCE)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (text == null || text.isBlank()) {
            sender.sendMessage(Lang.get("usage.announce"));
            return;
        }
        Component msg = Lang.get("announce.prefix", "text", text);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
        }
        Bukkit.getConsoleSender().sendMessage(msg);
        sender.sendMessage(Lang.get("announce.sent", "count", Bukkit.getOnlinePlayers().size()));
    }
}
