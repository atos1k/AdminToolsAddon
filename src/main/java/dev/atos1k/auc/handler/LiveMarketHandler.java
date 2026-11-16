package dev.atos1k.auc.handler;

import dev.atos1k.auc.util.AddonConfig;
import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.Formatters;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.LotScanner;
import dev.atos1k.auc.util.NameResolver;
import dev.by1337.auc.auc.ClientAucLot;
import dev.by1337.auc.auc.ClientItemStack;
import dev.by1337.auc.common.auc.log.LogRecord;
import dev.by1337.auc.common.auc.log.impl.AddLotLog;
import dev.by1337.auc.handler.Auction;
import dev.by1337.auc.handler.SimpleAuction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class LiveMarketHandler {
    private final AdminToolsAddon addon;

    public LiveMarketHandler(AdminToolsAddon addon) {
        this.addon = addon;
    }

    public void onRecord(Auction auction, LogRecord record) {
        if (!(record.log() instanceof AddLotLog log)) return;
        int count = Math.max(1, log.count());
        long unit = log.lprice() / count;
        long total = log.lprice();
        UUID actor = record.actor();
        auction.loadItem(log.item()).then(item -> {
            if (item == null) return;
            notifySubscribers(item, count, unit, total);
            checkDump(auction, item, actor, count, unit);
        });
    }

    private void notifySubscribers(ClientItemStack item, int count, long unit, long total) {
        if (!AddonConfig.b("alert.enabled", true)) return;
        List<UUID> targets = addon.alerts().matching(item.material().name(), unit);
        if (targets.isEmpty()) return;
        String itemName = item.itemNameNoColors();
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
            for (UUID id : targets) {
                Player player = Bukkit.getPlayer(id);
                if (player == null || !player.isOnline() || !player.hasPermission(Permissions.ALERT)) continue;
                Lang.send(player, "alert.hit", "item", itemName, "count", count,
                        "total", Formatters.moneyFromCents(total), "unit", Formatters.moneyFromCents(unit));
            }
        });
    }

    private void checkDump(Auction auction, ClientItemStack item, UUID actor, int count, long unit) {
        int percent = addon.limits().dumpPercent();
        if (percent <= 0 || !AddonConfig.b("dump-alert.enabled", true)) return;
        int minLots = AddonConfig.i("dump-alert.min-market-lots", 5);
        Material material = item.material();
        String itemName = item.itemNameNoColors();
        SimpleAuction.WORKER.execute(() -> {
            List<ClientAucLot> lots = LotScanner.collectByMaterial(auction, material);
            if (lots.size() < minLots) return;
            List<Long> prices = new ArrayList<>(lots.size());
            for (ClientAucLot lot : lots) prices.add(lot.centsPriceForOne());
            prices.sort(Long::compare);
            long median = prices.get(prices.size() / 2);
            if (median <= 0L || unit * 100L >= median * percent) return;
            NameResolver.resolve(auction, actor, new HashMap<>(), name -> {
                Object[] values = {"player", name != null ? name : "-",
                        "item", itemName, "count", count, "unit", Formatters.moneyFromCents(unit),
                        "median", Formatters.moneyFromCents(median)};
                Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.hasPermission(Permissions.DUMPALERT)) Lang.send(online, "dumpalert.line", values);
                    }
                    if (AddonConfig.b("dump-alert.notify-console", true)) {
                        Lang.send(Bukkit.getConsoleSender(), "dumpalert.line", values);
                    }
                });
            });
        });
    }
}
