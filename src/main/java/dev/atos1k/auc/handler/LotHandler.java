package dev.atos1k.auc.handler;

import dev.by1337.auc.BAuction;
import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.CommandUtil;
import dev.atos1k.auc.util.Formatters;
import dev.atos1k.auc.util.Messages;
import dev.by1337.auc.auc.ClientAucLot;
import dev.by1337.auc.common.auc.log.impl.AddLotLog;
import dev.by1337.auc.handler.Auction;
import dev.by1337.auc.handler.SimpleAuction;
import dev.by1337.auc.util.number.EconomyUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;


public class LotHandler {

    private final AdminToolsAddon addon;

    public LotHandler(AdminToolsAddon addon) {
        this.addon = addon;
    }


    public void handleLot(CommandSender sender, Auction auction, String[] args) {
        if (!sender.hasPermission(Permissions.LOT)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (args.length < 1 || !CommandUtil.isNumeric(args[0])) {
            sender.sendMessage(Messages.err("Использование: /baucadmin lot <id>"));
            return;
        }
        int uid = Integer.parseInt(args[0]);
        SimpleAuction.WORKER.execute(() -> {
            ClientAucLot lot = auction.getLot(uid);
            if (lot == null) {
                Bukkit.getScheduler().runTask(addon.getPlugin(), () -> sender.sendMessage(Messages.err("Лот #" + uid + " не найден (продан/снят/не существует).")));
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


    public void handleRemoveLot(CommandSender sender, Auction auction, String[] args) {
        if (!sender.hasPermission(Permissions.REMOVELOT)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (args.length < 1 || !CommandUtil.isNumeric(args[0])) {
            sender.sendMessage(Messages.err("Использование: /baucadmin removelot <id>"));
            return;
        }
        int uid = Integer.parseInt(args[0]);
        SimpleAuction.WORKER.execute(() -> {
            ClientAucLot lot = auction.getLot(uid);
            if (lot == null) {
                Bukkit.getScheduler().runTask(addon.getPlugin(), () -> sender.sendMessage(Messages.err("Лот #" + uid + " не найден.")));
                return;
            }
            auction.moveToVault(lot, lot.owner()).then(success -> Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                if (Boolean.TRUE.equals(success)) {
                    sender.sendMessage(Messages.info("Лот #" + uid + " снят с аукциона и перемещён в vault владельца."));
                } else {
                    sender.sendMessage(Messages.err("Не удалось снять лот #" + uid + "."));
                }
            }));
        });
    }
    
    public void handleGive(CommandSender sender, Auction auction, String[] args) {
        if (!sender.hasPermission(Permissions.GIVE)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(Messages.err("Эту команду можно использовать только в игре — нужен предмет в руке."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.err("Использование: /baucadmin give <ник> <цена> [кол-во] [часы]"));
            return;
        }
        String targetName = args[0];
        Double price = CommandUtil.parseDoubleOrNull(args[1]);
        if (price == null || price <= 0) {
            sender.sendMessage(Messages.err("Некорректная цена."));
            return;
        }
        int count = 1;
        if (args.length > 2 && CommandUtil.isNumeric(args[2])) count = CommandUtil.clamp(Integer.parseInt(args[2]), 1, 6400);
        Long durationHours = null;
        if (args.length > 3 && CommandUtil.isNumeric(args[3])) durationHours = (long) CommandUtil.clamp(Integer.parseInt(args[3]), 1, 24 * 30);

        ItemStack held = admin.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            sender.sendMessage(Messages.err("Возьмите в руку предмет, который хотите выставить лотом."));
            return;
        }
        ItemStack template = held.asOne();
        int finalCount = count;
        long lprice = EconomyUtil.toCents(price);
        double finalPrice = price;
        long sellingDuration = durationHours != null ? durationHours * 3_600_000L : BAuction.plugin().config().selling_duration;

        auction.findUUID(targetName).then(pair -> {
            if (pair == null) {
                sender.sendMessage(Messages.err("Игрок не найден: " + targetName));
                return;
            }
            UUID targetUuid = pair.getKey();
            auction.addLot(template, targetUuid, sellingDuration, finalCount, lprice).then(ghostLot -> {
                if (ghostLot == null) {
                    sender.sendMessage(Messages.err("Не удалось создать лот (аукцион отключён или отклонена цена)."));
                    return;
                }
                auction.publishLog(new AddLotLog(System.currentTimeMillis(), targetUuid, lprice, ghostLot.itemStack().id(), finalCount));
                sender.sendMessage(Messages.info("Лот создан: " + template.getType().name() + " x" + finalCount
                        + " для " + targetName + " за " + Formatters.money(finalPrice) + ". Ваш инвентарь не тронут."));
            });
        });
    }
}
