package dev.atos1k.auc.command;

import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.util.BanTypes;
import dev.atos1k.auc.util.Formatters;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.Messages;
import dev.by1337.auc.BAuction;
import dev.by1337.auc.auc.ClientAucLot;
import dev.by1337.auc.auc.ClientVaultLot;
import dev.by1337.bmenu.command.ExecuteContext;
import dev.by1337.cmd.Command;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class MenuCommands {
    private static final long CONFIRM_WINDOW = 5000L;
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private MenuCommands() {
    }

    private record Pending(int uid, long at) {
    }

    public static Command<ExecuteContext> install(AdminToolsAddon addon, Command<ExecuteContext> base) {
        base.sub(new Command<ExecuteContext>("[bauc_admin_info]").executor(ctx -> {
            Player viewer = ctx.menu.viewer();
            if (!viewer.hasPermission(Permissions.BASE)) return;
            if (!viewer.hasPermission(Permissions.LOT)) {
                viewer.sendMessage(Messages.deny());
                return;
            }
            Object payload = ctx.menu.lastClickedItemPayload();
            if (payload instanceof ClientAucLot lot) {
                sendLotInfo(viewer, lot);
            } else if (payload instanceof ClientVaultLot lot) {
                viewer.sendMessage(Messages.header(Lang.rawFormatted("menu.vault-header",
                        "item", lot.itemStack().itemNameNoColors())));
                viewer.sendMessage(Messages.kv(Lang.raw("menu.item-id"), String.valueOf(lot.itemStack().id())));
                viewer.sendMessage(Messages.kv(Lang.raw("menu.count"), String.valueOf(lot.count())));
                viewer.sendMessage(Messages.kv(Lang.raw("menu.price"), Formatters.moneyFromCents(lot.lprice())));
            } else {
                viewer.sendMessage(Lang.get("menu.not-a-lot"));
            }
        }));

        base.sub(new Command<ExecuteContext>("[bauc_admin_remove_lot]").executor(ctx -> {
            Player viewer = ctx.menu.viewer();
            if (!viewer.hasPermission(Permissions.BASE)) return;
            if (!viewer.hasPermission(Permissions.REMOVELOT)) {
                viewer.sendMessage(Messages.deny());
                return;
            }
            var auction = BAuction.auction();
            if (auction == null) {
                viewer.sendMessage(Lang.get("general.auction-not-ready"));
                return;
            }
            if (!(ctx.menu.lastClickedItemPayload() instanceof ClientAucLot lot)) {
                viewer.sendMessage(Lang.get("menu.not-a-lot"));
                return;
            }
            Pending pending = PENDING.get(viewer.getUniqueId());
            long now = System.currentTimeMillis();
            if (pending == null || pending.uid() != lot.uid() || now - pending.at() > CONFIRM_WINDOW) {
                PENDING.put(viewer.getUniqueId(), new Pending(lot.uid(), now));
                viewer.sendMessage(Lang.get("menu.remove-confirm", "id", lot.uid()));
                return;
            }
            PENDING.remove(viewer.getUniqueId());
            auction.moveToVault(lot, lot.owner()).then(success -> {
                if (Boolean.TRUE.equals(success)) {
                    viewer.sendMessage(Lang.get("lot.removelot-success", "id", lot.uid()));
                    if (ctx.menu.isOpened()) ctx.menu.refresh();
                } else {
                    viewer.sendMessage(Lang.get("lot.removelot-fail", "id", lot.uid()));
                }
            });
        }));

        base.sub(new Command<ExecuteContext>("[bauc_admin_ban_owner]").executor(ctx -> {
            Player viewer = ctx.menu.viewer();
            if (!viewer.hasPermission(Permissions.BASE)) return;
            if (!viewer.hasPermission(Permissions.BAN)) {
                viewer.sendMessage(Messages.deny());
                return;
            }
            if (!(ctx.menu.lastClickedItemPayload() instanceof ClientAucLot lot)) {
                viewer.sendMessage(Lang.get("menu.not-a-lot"));
                return;
            }
            var auction = BAuction.auction();
            if (auction == null) {
                viewer.sendMessage(Lang.get("general.auction-not-ready"));
                return;
            }
            auction.loadName(lot.owner()).then(playerName -> {
                String name = playerName != null ? playerName.name() : lot.owner().toString();
                addon.bans().ban(lot.owner(), name, BanTypes.ALL, 0, null);
                viewer.sendMessage(Lang.get("ban.success", "player", name,
                        "type", Lang.raw("ban.types.all"), "period", Lang.raw("ban.forever")));
            });
        }));

        return base;
    }

    private static void sendLotInfo(Player viewer, ClientAucLot lot) {
        viewer.sendMessage(Messages.header(Lang.rawFormatted("menu.lot-header", "id", lot.uid())));
        viewer.sendMessage(Messages.kv(Lang.raw("menu.owner"), lot.ownerName() + " (" + lot.owner() + ")"));
        viewer.sendMessage(Messages.kv(Lang.raw("menu.item"),
                lot.itemStack().itemNameNoColors() + " x" + lot.count()));
        viewer.sendMessage(Messages.kv(Lang.raw("menu.item-id"), String.valueOf(lot.itemStack().id())));
        viewer.sendMessage(Messages.kv(Lang.raw("menu.price"),
                Formatters.money(lot.dprice()) + " (" + Formatters.money(lot.dprice_for_one()) + " за шт.)"));
        viewer.sendMessage(Messages.kv(Lang.raw("menu.created"), Formatters.date(lot.createdDate())));
        viewer.sendMessage(Messages.kv(Lang.raw("menu.expires"), Formatters.date(lot.removalDate())));
        viewer.sendMessage(Lang.get("menu.history-hint", "id", lot.itemStack().id()));
    }
}
