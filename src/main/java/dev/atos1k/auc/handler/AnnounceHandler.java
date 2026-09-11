package dev.atos1k.auc.handler;

import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AnnounceHandler {

    public void handleAnnounce(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ANNOUNCE)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(Messages.err("Использование: /baucadmin announce <текст>"));
            return;
        }
        String text = String.join(" ", args);
        Component msg = Component.text("[Аукцион] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(text, NamedTextColor.YELLOW));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
        }
        Bukkit.getConsoleSender().sendMessage(msg);
        sender.sendMessage(Messages.info("Объявление отправлено всем игрокам (" + Bukkit.getOnlinePlayers().size() + ")."));
    }
}
