package dev.atos1k.auc.handler;

import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.storage.BanEntry;
import dev.atos1k.auc.util.BanTypes;
import dev.atos1k.auc.util.DurationUtil;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.Messages;
import dev.by1337.auc.handler.Auction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BanHandler {
    private final AdminToolsAddon addon;

    public BanHandler(AdminToolsAddon addon) {
        this.addon = addon;
    }

    public void handleBan(CommandSender sender, Auction auction, String name, String typeId,
                          String durationArg, String reason) {
        if (!sender.hasPermission(Permissions.BAN)) {
            Lang.send(sender, "general.no-permission");
            return;
        }
        if (name == null) {
            Lang.send(sender, "usage.ban");
            return;
        }
        BanTypes type = BanTypes.ALL;
        if (typeId != null) {
            type = BanTypes.byId(typeId);
            if (type == null) {
                Lang.send(sender, "ban.unknown-type", "type", typeId,
                        "types", String.join(", ", BanTypes.ids()));
                return;
            }
        }
        long duration = durationArg == null ? 0 : DurationUtil.parse(durationArg);
        if (duration < 0) {
            Lang.send(sender, "ban.bad-duration", "duration", durationArg);
            return;
        }
        BanTypes finalType = type;
        long finalDuration = duration;
        auction.findUUID(name).then(pair -> {
            if (pair == null) {
                Lang.send(sender, "general.player-not-found", "player", name);
                return;
            }
            UUID uuid = pair.getKey();
            String realName = pair.getValue() != null ? pair.getValue() : name;
            BanEntry entry = addon.bans().ban(uuid, realName, finalType, finalDuration, reason);

            String typeName = Lang.raw("ban.types." + finalType.id());
            String period = entry.permanent()
                    ? Lang.raw("ban.forever")
                    : DurationUtil.format(entry.expires() - System.currentTimeMillis());
            Lang.send(sender, "ban.success", "player", realName, "type", typeName, "period", period);
            if (reason != null) {
                Lang.send(sender, "ban.success-reason", "reason", reason);
            }
            Player target = Bukkit.getPlayer(uuid);
            if (target != null) {
                Lang.send(target, "ban.denied." + finalType.id());
                if (reason != null) Lang.send(target, "ban.denied-reason", "reason", reason);
                if (entry.permanent()) {
                    Lang.send(target, "ban.denied-permanent");
                } else {
                    Lang.send(target, "ban.denied-until", "time", period);
                }
            }
        });
    }

    public void handleUnban(CommandSender sender, Auction auction, String name, String typeId) {
        if (!sender.hasPermission(Permissions.BAN)) {
            Lang.send(sender, "general.no-permission");
            return;
        }
        if (name == null) {
            Lang.send(sender, "usage.unban");
            return;
        }
        BanTypes type = null;
        if (typeId != null) {
            type = BanTypes.byId(typeId);
            if (type == null) {
                Lang.send(sender, "ban.unknown-type", "type", typeId,
                        "types", String.join(", ", BanTypes.ids()));
                return;
            }
        }
        BanTypes finalType = type;
        auction.findUUID(name).then(pair -> {
            if (pair == null) {
                Lang.send(sender, "general.player-not-found", "player", name);
                return;
            }
            UUID uuid = pair.getKey();
            String realName = pair.getValue() != null ? pair.getValue() : name;
            if (!addon.bans().unban(uuid, finalType)) {
                Lang.send(sender, "ban.not-banned", "player", realName);
                return;
            }
            if (finalType == null || finalType == BanTypes.ALL) {
                Lang.send(sender, "ban.unban-all", "player", realName);
            } else {
                Lang.send(sender, "ban.unban", "player", realName,
                        "type", Lang.raw("ban.types." + finalType.id()));
            }
        });
    }

    public void handleBans(CommandSender sender) {
        if (!sender.hasPermission(Permissions.BAN)) {
            Lang.send(sender, "general.no-permission");
            return;
        }
        Map<UUID, List<BanEntry>> all = addon.bans().all();
        if (all.isEmpty()) {
            Lang.send(sender, "ban.list-empty");
            return;
        }
        sender.sendMessage(Messages.header(Lang.rawFormatted("ban.list-header", "count", all.size())));
        for (Map.Entry<UUID, List<BanEntry>> player : all.entrySet()) {
            List<String> parts = new ArrayList<>();
            String name = null;
            for (BanEntry entry : player.getValue()) {
                if (entry.name() != null) name = entry.name();
                String period = entry.permanent()
                        ? Lang.raw("ban.forever")
                        : DurationUtil.format(entry.expires() - System.currentTimeMillis());
                parts.add(Lang.raw("ban.types." + entry.type().id()) + " (" + period + ")");
            }
            Lang.send(sender, "ban.list-line",
                    "player", name != null ? name : player.getKey().toString(),
                    "types", String.join(", ", parts));
            for (BanEntry entry : player.getValue()) {
                if (entry.reason() != null && !entry.reason().isBlank()) {
                    Lang.send(sender, "ban.list-reason",
                            "type", Lang.raw("ban.types." + entry.type().id()), "reason", entry.reason());
                }
            }
        }
    }
}
