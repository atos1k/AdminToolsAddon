package dev.atos1k.auc.handler;

import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.Formatters;
import dev.atos1k.auc.util.LogTypes;
import dev.atos1k.auc.util.Messages;
import dev.atos1k.auc.util.NameResolver;
import dev.by1337.auc.common.auc.log.AuctionLog;
import dev.by1337.auc.common.auc.log.LogRecord;
import dev.by1337.auc.common.auc.log.impl.WithItemStackLog;
import dev.by1337.auc.common.auc.log.impl.WithLPriceLog;
import dev.by1337.auc.handler.Auction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WatchHandler {

    private final AdminToolsAddon addon;

    public WatchHandler(AdminToolsAddon addon) {
        this.addon = addon;
    }

    public void handleWatch(CommandSender sender) {
        if (!sender.hasPermission(Permissions.WATCH)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        boolean nowWatching;
        if (sender instanceof Player player) {
            nowWatching = addon.toggleWatcher(player.getUniqueId());
        } else {
            nowWatching = addon.toggleConsoleWatch();
        }
        if (nowWatching) {
            sender.sendMessage(Messages.info("Живая лента транзакций включена. Используйте /baucadmin watch ещё раз, чтобы отключить."));
        } else {
            sender.sendMessage(Messages.info("Живая лента транзакций отключена."));
        }
    }
    
    public void broadcastLiveEvent(Auction auction, LogRecord record) {
        AuctionLog log = record.log();
        Map<UUID, String> tempCache = new HashMap<>();
        NameResolver.resolve(auction, record.actor(), tempCache, actorName -> {
            String base = "[" + LogTypes.describe(log.type()) + "] " + (actorName != null ? actorName : "-");
            if (log instanceof WithItemStackLog withItem) {
                auction.loadItem(withItem.item()).then(item -> {
                    String itemName = item != null ? item.itemNameNoColors() : "?";
                    String priceStr = log instanceof WithLPriceLog withPrice ? Formatters.moneyFromCents(withPrice.lprice()) : null;
                    StringBuilder line = new StringBuilder(base).append("  ").append(itemName).append(" x").append(withItem.count());
                    if (priceStr != null) line.append("  ").append(priceStr);
                    Component c = Component.text("[live] ", NamedTextColor.LIGHT_PURPLE).append(Component.text(line.toString(), NamedTextColor.GRAY));
                    Bukkit.getScheduler().runTask(addon.getPlugin(), () -> addon.dispatchToWatchers(c));
                });
            } else {
                Component c = Component.text("[live] ", NamedTextColor.LIGHT_PURPLE).append(Component.text(base, NamedTextColor.GRAY));
                Bukkit.getScheduler().runTask(addon.getPlugin(), () -> addon.dispatchToWatchers(c));
            }
        });
    }
}
