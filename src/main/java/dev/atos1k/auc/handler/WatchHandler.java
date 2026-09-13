package dev.atos1k.auc.handler;

import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.Formatters;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.LogTypes;
import dev.atos1k.auc.util.Messages;
import dev.atos1k.auc.util.NameResolver;
import dev.atos1k.auc.util.WatchFilter;
import dev.by1337.auc.common.auc.log.AuctionLog;
import dev.by1337.auc.common.auc.log.LogRecord;
import dev.by1337.auc.common.auc.log.impl.WithItemStackLog;
import dev.by1337.auc.common.auc.log.impl.WithLPriceLog;
import dev.by1337.auc.handler.Auction;
import dev.by1337.auc.util.number.EconomyUtil;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WatchHandler {
    private final AdminToolsAddon addon;

    public WatchHandler(AdminToolsAddon addon) {
        this.addon = addon;
    }

    private UUID keyOf(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId() : null;
    }

    public void handleWatch(CommandSender sender, String typeAlias) {
        if (!sender.hasPermission(Permissions.WATCH)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (typeAlias == null) {
            boolean nowWatching = sender instanceof Player player
                    ? addon.toggleWatcher(player.getUniqueId())
                    : addon.toggleConsoleWatch();
            sender.sendMessage(nowWatching ? Lang.get("watch.enabled") : Lang.get("watch.disabled"));
            return;
        }
        if (addon.watchFilter(keyOf(sender)) == null) {
            if (sender instanceof Player player) {
                addon.toggleWatcher(player.getUniqueId());
            } else {
                addon.toggleConsoleWatch();
            }
            sender.sendMessage(Lang.get("watch.enabled"));
        }
        handleWatchType(sender, typeAlias);
    }

    public void handleWatchOff(CommandSender sender) {
        if (!sender.hasPermission(Permissions.WATCH)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        UUID key = keyOf(sender);
        if (addon.watchFilter(key) == null) {
            sender.sendMessage(Lang.get("watch.disabled"));
            return;
        }
        if (sender instanceof Player player) {
            addon.toggleWatcher(player.getUniqueId());
        } else {
            addon.toggleConsoleWatch();
        }
        sender.sendMessage(Lang.get("watch.disabled"));
    }

    public void handleWatchClear(CommandSender sender) {
        if (!sender.hasPermission(Permissions.WATCH)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        UUID key = keyOf(sender);
        if (addon.watchFilter(key) == null) {
            sender.sendMessage(Lang.get("watch.not-watching"));
            return;
        }
        addon.setWatchFilter(key, WatchFilter.NONE);
        sender.sendMessage(Lang.get("watch.filters-cleared"));
    }

    public void handleWatchPlayer(CommandSender sender, Auction auction, String name) {
        if (!sender.hasPermission(Permissions.WATCH)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (name == null) {
            sender.sendMessage(Lang.get("usage.watch-player"));
            return;
        }
        UUID key = keyOf(sender);
        auction.findUUID(name).then(pair -> {
            if (pair == null) {
                sender.sendMessage(Lang.get("general.player-not-found", "player", name));
                return;
            }
            String realName = pair.getValue() != null ? pair.getValue() : name;
            WatchFilter current = addon.watchFilter(key);
            addon.setWatchFilter(key, (current == null ? WatchFilter.NONE : current)
                    .withPlayer(pair.getKey(), realName));
            sender.sendMessage(Lang.get("watch.filter-player", "player", realName));
        });
    }

    public void handleWatchType(CommandSender sender, String typeAlias) {
        if (!sender.hasPermission(Permissions.WATCH)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (typeAlias == null) {
            sender.sendMessage(Lang.get("usage.watch-type"));
            return;
        }
        String alias = typeAlias.toLowerCase(Locale.ROOT);
        String typeId = LogTypes.TYPE_ALIASES.get(alias);
        if (typeId == null) {
            sender.sendMessage(Lang.get("watch.unknown-type", "type", typeAlias,
                    "types", String.join(", ", LogTypes.TYPE_ALIASES.keySet())));
            return;
        }
        UUID key = keyOf(sender);
        WatchFilter current = addon.watchFilter(key);
        addon.setWatchFilter(key, (current == null ? WatchFilter.NONE : current).withType(typeId));
        sender.sendMessage(Lang.get("watch.filter-type", "type", LogTypes.describe(typeId)));
    }

    public void handleWatchMin(CommandSender sender, Double price) {
        if (!sender.hasPermission(Permissions.WATCH)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (price == null || price < 0) {
            sender.sendMessage(Lang.get("usage.watch-min"));
            return;
        }
        UUID key = keyOf(sender);
        WatchFilter current = addon.watchFilter(key);
        addon.setWatchFilter(key, (current == null ? WatchFilter.NONE : current)
                .withMin(EconomyUtil.toCents(price)));
        sender.sendMessage(Lang.get("watch.filter-min", "price", Formatters.money(price)));
    }

    public void handleWatchStatus(CommandSender sender) {
        if (!sender.hasPermission(Permissions.WATCH)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        WatchFilter filter = addon.watchFilter(keyOf(sender));
        if (filter == null) {
            sender.sendMessage(Lang.get("watch.not-watching"));
            return;
        }
        sender.sendMessage(Messages.header(Lang.raw("watch.status-header")));
        sender.sendMessage(Lang.get("watch.status-player", "player",
                filter.playerName() != null ? filter.playerName() : Lang.raw("watch.status-any")));
        sender.sendMessage(Lang.get("watch.status-type", "type",
                filter.type() != null ? LogTypes.describe(filter.type()) : Lang.raw("watch.status-any")));
        sender.sendMessage(Lang.get("watch.status-min", "price",
                filter.minCents() != null ? Formatters.moneyFromCents(filter.minCents()) : Lang.raw("watch.status-any")));
    }

    public void broadcastLiveEvent(Auction auction, LogRecord record) {
        AuctionLog log = record.log();
        String typeId = log.type();
        long priceCents = log instanceof WithLPriceLog withPrice ? withPrice.lprice() : 0;
        Map<UUID, String> tempCache = new HashMap<>();
        NameResolver.resolve(auction, record.actor(), tempCache, actorName -> {
            String base = "[" + LogTypes.describe(typeId) + "] " + (actorName != null ? actorName : "-");
            if (log instanceof WithItemStackLog withItem) {
                auction.loadItem(withItem.item()).then(item -> {
                    String itemName = item != null ? item.itemNameNoColors() : "?";
                    StringBuilder line = new StringBuilder(base).append("  ").append(itemName).append(" x").append(withItem.count());
                    if (log instanceof WithLPriceLog withPrice) {
                        line.append("  ").append(Formatters.moneyFromCents(withPrice.lprice()));
                    }
                    Component c = Lang.get("watch.live-line", "text", line.toString());
                    Bukkit.getScheduler().runTask(addon.getPlugin(), () ->
                            addon.dispatchToWatchers(c, record.actor(), record.subject(), typeId, priceCents));
                });
            } else {
                Component c = Lang.get("watch.live-line", "text", base);
                Bukkit.getScheduler().runTask(addon.getPlugin(), () ->
                        addon.dispatchToWatchers(c, record.actor(), record.subject(), typeId, priceCents));
            }
        });
    }
}
