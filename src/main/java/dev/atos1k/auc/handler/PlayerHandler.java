package dev.atos1k.auc.handler;

import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.Formatters;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.LotScanner;
import dev.atos1k.auc.util.Messages;
import dev.by1337.auc.auc.ClientAucLot;
import dev.by1337.auc.auc.ClientVaultLot;
import dev.by1337.auc.common.auc.log.LogQuery;
import dev.by1337.auc.common.auc.log.LogRecord;
import dev.by1337.auc.common.auc.log.impl.BuyAuctionLog;
import dev.by1337.auc.common.auc.log.impl.WithLPriceLog;
import dev.by1337.auc.handler.Auction;
import dev.by1337.auc.handler.SimpleAuction;
import dev.by1337.auc.search.PlayerVaultResult;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public class PlayerHandler {
    private final AdminToolsAddon addon;

    public PlayerHandler(AdminToolsAddon addon) {
        this.addon = addon;
    }
    
    public void handlePlayer(CommandSender sender, Auction auction, String name) {
        if (!sender.hasPermission(Permissions.PLAYER)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (name == null) {
            sender.sendMessage(Lang.get("usage.player"));
            return;
        }
        auction.findUUID(name).then(pair -> {
            if (pair == null) {
                sender.sendMessage(Lang.get("general.player-not-found", "player", name));
                return;
            }
            UUID uuid = pair.getKey();

            LogQuery spentQuery = new LogQuery(null, null, null, null, uuid, null, BuyAuctionLog.ID, 100);
            LogQuery earnedQuery = new LogQuery(null, null, null, null, null, uuid, BuyAuctionLog.ID, 100);

            auction.loadLogs(spentQuery).then(spentLogs ->
                    auction.loadLogs(earnedQuery).then(earnedLogs -> {
                        long spentCents = 0;
                        int purchases = spentLogs == null ? 0 : spentLogs.size();
                        if (spentLogs != null) {
                            for (LogRecord r : spentLogs) {
                                if (r.log() instanceof WithLPriceLog p) spentCents += p.lprice();
                            }
                        }
                        long earnedCents = 0;
                        int sales = earnedLogs == null ? 0 : earnedLogs.size();
                        if (earnedLogs != null) {
                            for (LogRecord r : earnedLogs) {
                                if (r.log() instanceof WithLPriceLog p) earnedCents += p.lprice();
                            }
                        }

                        long finalSpentCents = spentCents;
                        long finalEarnedCents = earnedCents;
                        int finalPurchases = purchases;
                        int finalSales = sales;

                        SimpleAuction.WORKER.execute(() -> {
                            PlayerVaultResult vaultResult = auction.playerVaultLots(uuid);
                            int vaultCount = 0;
                            long vaultValueCents = 0;
                            ClientVaultLot vlot;
                            while ((vlot = vaultResult.next()) != null) {
                                vaultCount++;
                                vaultValueCents += vlot.lprice();
                            }
                            vaultResult.release();

                            int finalVaultCount = vaultCount;
                            long finalVaultValueCents = vaultValueCents;
                            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                                sender.sendMessage(Messages.header("Торговый профиль: " + name));
                                sender.sendMessage(Messages.kv("UUID", uuid.toString()));
                                sender.sendMessage(Messages.kv("Потрачено (посл. до 100 покупок)", Formatters.moneyFromCents(finalSpentCents) + "  (" + finalPurchases + " шт.)"));
                                sender.sendMessage(Messages.kv("Заработано (посл. до 100 продаж)", Formatters.moneyFromCents(finalEarnedCents) + "  (" + finalSales + " шт.)"));
                                sender.sendMessage(Messages.kv("Предметов в vault", String.valueOf(finalVaultCount)));
                                sender.sendMessage(Messages.kv("Стоимость vault", Formatters.moneyFromCents(finalVaultValueCents)));
                                sender.sendMessage(Lang.get("player.active-lots-hint", "player", name));
                                sender.sendMessage(Lang.get("player.full-history-hint", "player", name));
                            });
                        });
                    }));
        });
    }
    
    public void handleWipe(CommandSender sender, Auction auction, String name, String confirm) {
        if (!sender.hasPermission(Permissions.WIPE)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (name == null) {
            sender.sendMessage(Lang.get("usage.wipe"));
            return;
        }
        boolean confirmed = confirm != null && confirm.equalsIgnoreCase("confirm");
        if (!confirmed) {
            sender.sendMessage(Lang.get("player.wipe-confirm-warning1", "player", name));
            sender.sendMessage(Lang.get("player.wipe-confirm-warning2", "player", name));
            return;
        }
        auction.findUUID(name).then(pair -> {
            if (pair == null) {
                sender.sendMessage(Lang.get("general.player-not-found", "player", name));
                return;
            }
            UUID uuid = pair.getKey();
            SimpleAuction.WORKER.execute(() -> {
                List<ClientAucLot> ownerLots = LotScanner.collectOwnedBy(auction, uuid);

                if (ownerLots.isEmpty()) {
                    Bukkit.getScheduler().runTask(addon.getPlugin(), () -> sender.sendMessage(Lang.get("player.wipe-none", "player", name)));
                    return;
                }
                int total = ownerLots.size();
                AtomicInteger okCount = new AtomicInteger();
                AtomicInteger failCount = new AtomicInteger();
                auction.parallel(
                        ownerLots.iterator(),
                        () -> Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                            String errorsSuffix = failCount.get() > 0
                                    ? Lang.rawFormatted("player.wipe-done-errors-suffix", "errors", failCount.get())
                                    : "";
                            sender.sendMessage(Lang.get("player.wipe-done", "ok", okCount.get(), "total", total,
                                    "player", name, "errors", errorsSuffix));
                        }),
                        l -> auction.moveToVault(l, uuid),
                        (l, success) -> {
                            if (Boolean.TRUE.equals(success)) okCount.incrementAndGet();
                            else failCount.incrementAndGet();
                        }
                );
            });
        });
    }
}
