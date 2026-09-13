package dev.atos1k.auc.handler;

import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.CommandUtil;
import dev.atos1k.auc.util.Formatters;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.Messages;
import dev.by1337.auc.BAuction;
import dev.by1337.auc.auc.ClientAucLot;
import dev.by1337.auc.common.auc.log.impl.AddLotLog;
import dev.by1337.auc.handler.Auction;
import dev.by1337.auc.handler.SimpleAuction;
import dev.by1337.auc.util.number.EconomyUtil;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class LotHandler {
    private final AdminToolsAddon addon;

    public LotHandler(AdminToolsAddon addon) {
        this.addon = addon;
    }

    public void handleLot(CommandSender sender, Auction auction, Integer id) {
        if (!sender.hasPermission(Permissions.LOT)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (id == null) {
            sender.sendMessage(Lang.get("usage.lot"));
            return;
        }
        int uid = id;
        SimpleAuction.WORKER.execute(() -> {
            ClientAucLot lot = auction.getLot(uid);
            if (lot == null) {
                Bukkit.getScheduler().runTask(addon.getPlugin(), () -> sender.sendMessage(Lang.get("lot.not-found", "id", uid)));
                return;
            }
            auction.loadName(lot.owner()).then(ownerName -> Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                sender.sendMessage(Messages.header("Лот #" + uid));
                sender.sendMessage(Messages.kv("Владелец", (ownerName != null ? ownerName.name() : "?") + " (" + lot.owner() + ")"));
                sender.sendMessage(Messages.kv("Предмет", lot.itemStack().itemNameNoColors() + " x" + lot.count()));
                sender.sendMessage(Messages.kv("Цена", Formatters.money(lot.dprice()) + "  (" + Formatters.money(lot.dprice_for_one()) + " за шт.)"));
                sender.sendMessage(Messages.kv("Создан", Formatters.date(lot.createdDate())));
                sender.sendMessage(Messages.kv("Истекает", Formatters.date(lot.removalDate())));
            }));
        });
    }

    public void handleRemoveLot(CommandSender sender, Auction auction, Integer id) {
        if (!sender.hasPermission(Permissions.REMOVELOT)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (id == null) {
            sender.sendMessage(Lang.get("usage.removelot"));
            return;
        }
        int uid = id;
        SimpleAuction.WORKER.execute(() -> {
            ClientAucLot lot = auction.getLot(uid);
            if (lot == null) {
                Bukkit.getScheduler().runTask(addon.getPlugin(), () -> sender.sendMessage(Lang.get("lot.removelot-not-found", "id", uid)));
                return;
            }
            auction.moveToVault(lot, lot.owner()).then(success -> Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                if (Boolean.TRUE.equals(success)) {
                    sender.sendMessage(Lang.get("lot.removelot-success", "id", uid));
                } else {
                    sender.sendMessage(Lang.get("lot.removelot-fail", "id", uid));
                }
            }));
        });
    }
    
    public void handleGive(CommandSender sender, Auction auction, String targetName, Double price,
                           Integer countArg, Integer hoursArg) {
        if (!sender.hasPermission(Permissions.GIVE)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(Lang.get("lot.give-not-player"));
            return;
        }
        if (targetName == null || price == null) {
            sender.sendMessage(Lang.get("usage.give"));
            return;
        }
        if (price <= 0) {
            sender.sendMessage(Lang.get("lot.give-bad-price"));
            return;
        }
        int count = countArg == null ? 1 : CommandUtil.clamp(countArg, 1, 6400);
        Long durationHours = null;
        if (hoursArg != null) durationHours = (long) CommandUtil.clamp(hoursArg, 1, 24 * 30);

        ItemStack held = admin.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            sender.sendMessage(Lang.get("lot.give-empty-hand"));
            return;
        }
        ItemStack template = held.asOne();
        int finalCount = count;
        long lprice = EconomyUtil.toCents(price);
        double finalPrice = price;
        long sellingDuration = durationHours != null ? durationHours * 3_600_000L : BAuction.plugin().config().selling_duration;

        auction.findUUID(targetName).then(pair -> {
            if (pair == null) {
                sender.sendMessage(Lang.get("general.player-not-found", "player", targetName));
                return;
            }
            UUID targetUuid = pair.getKey();
            auction.addLot(template, targetUuid, sellingDuration, finalCount, lprice).then(ghostLot -> {
                if (ghostLot == null) {
                    sender.sendMessage(Lang.get("lot.give-fail"));
                    return;
                }
                auction.publishLog(new AddLotLog(System.currentTimeMillis(), targetUuid, lprice, ghostLot.itemStack().id(), finalCount));
                sender.sendMessage(Lang.get("lot.give-success", "item", template.getType().name(), "count", finalCount,
                        "player", targetName, "price", Formatters.money(finalPrice)));
            });
        });
    }
}
