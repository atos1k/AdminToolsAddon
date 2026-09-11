package dev.atos1k.auc.handler;

import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.Formatters;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerHandler {

    private final AdminToolsAddon addon;

    public PlayerHandler(AdminToolsAddon addon) {
        this.addon = addon;
    }
    
    public void handlePlayer(CommandSender sender, Auction auction, String[] args) {
        if (!sender.hasPermission(Permissions.PLAYER)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(Messages.err("Использование: /baucadmin player <ник>"));
            return;
        }
        String name = args[0];
        auction.findUUID(name).then(pair -> {
            if (pair == null) {
                sender.sendMessage(Messages.err("Игрок не найден: " + name));
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
                                sender.sendMessage(Component.text("Активные лоты — как обычно: ", NamedTextColor.GRAY)
                                        .append(Component.text("/ah " + name, NamedTextColor.YELLOW)));
                                sender.sendMessage(Component.text("Полная история: ", NamedTextColor.GRAY)
                                        .append(Component.text("/baucadmin transactions 50 " + name, NamedTextColor.YELLOW)));
                            });
                        });
                    }));
        });
    }
    
    public void handleWipe(CommandSender sender, Auction auction, String[] args) {
        if (!sender.hasPermission(Permissions.WIPE)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(Messages.err("Использование: /baucadmin wipe <ник> confirm"));
            return;
        }
        String name = args[0];
        boolean confirmed = args.length > 1 && args[1].equalsIgnoreCase("confirm");
        if (!confirmed) {
            sender.sendMessage(Messages.err("Это снимет ВСЕ активные лоты игрока " + name + " и перенесёт их в его vault."));
            sender.sendMessage(Messages.err("Повторите с подтверждением: /baucadmin wipe " + name + " confirm"));
            return;
        }
        auction.findUUID(name).then(pair -> {
            if (pair == null) {
                sender.sendMessage(Messages.err("Игрок не найден: " + name));
                return;
            }
            UUID uuid = pair.getKey();
            SimpleAuction.WORKER.execute(() -> {
                List<ClientAucLot> ownerLots = LotScanner.collectOwnedBy(auction, uuid);

                if (ownerLots.isEmpty()) {
                    Bukkit.getScheduler().runTask(addon.getPlugin(), () -> sender.sendMessage(Messages.info("У игрока " + name + " нет активных лотов.")));
                    return;
                }
                int total = ownerLots.size();
                AtomicInteger okCount = new AtomicInteger();
                AtomicInteger failCount = new AtomicInteger();
                auction.parallel(
                        ownerLots.iterator(),
                        () -> Bukkit.getScheduler().runTask(addon.getPlugin(), () ->
                                sender.sendMessage(Messages.info("Готово: снято " + okCount.get() + "/" + total + " лотов игрока " + name
                                        + (failCount.get() > 0 ? (", ошибок: " + failCount.get()) : "") + "."))),
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
