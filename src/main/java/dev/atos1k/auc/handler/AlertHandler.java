package dev.atos1k.auc.handler;

import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.command.args.ArgumentMaterial;
import dev.atos1k.auc.manager.PriceAlerts;
import dev.atos1k.auc.util.Formatters;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.Messages;
import dev.by1337.auc.util.number.EconomyUtil;
import dev.by1337.cmd.Command;
import dev.by1337.core.command.bcmd.argument.ArgumentDouble;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AlertHandler {
    private final AdminToolsAddon addon;

    private AlertHandler(AdminToolsAddon addon) {
        this.addon = addon;
    }

    public static void install(AdminToolsAddon addon, Command<CommandSender> base) {
        AlertHandler handler = new AlertHandler(addon);
        base.sub(new Command<CommandSender>("alert").alias("alerts")
                .requires(sender -> sender.hasPermission(Permissions.ALERT))
                .sub(new Command<CommandSender>("add").executor(
                        new ArgumentMaterial<>("material"), new ArgumentDouble<>("price"), handler::add))
                .sub(new Command<CommandSender>("remove").alias("rm").executor(
                        new ArgumentMaterial<>("material"), handler::remove))
                .sub(new Command<CommandSender>("list").executor(handler::list))
                .executor(handler::list));
    }

    private void add(CommandSender sender, Material material, Double price) {
        if (!(sender instanceof Player player)) return;
        if (!player.hasPermission(Permissions.ALERT)) {
            Lang.send(sender, "general.no-permission");
            return;
        }
        if (material == null || price == null || price <= 0) {
            Lang.send(sender, "usage.alert");
            return;
        }
        boolean added = addon.alerts().add(player.getUniqueId(), material.name(), EconomyUtil.toCents(price));
        if (added) {
            Lang.send(sender, "alert.added", "material", material.name(), "price", Formatters.money(price));
        } else {
            Lang.send(sender, "alert.limit", "limit", PriceAlerts.maxPerPlayer());
        }
    }

    private void remove(CommandSender sender, Material material) {
        if (!(sender instanceof Player player)) return;
        if (material == null) {
            Lang.send(sender, "usage.alert");
            return;
        }
        boolean removed = addon.alerts().remove(player.getUniqueId(), material.name());
        Lang.send(sender, removed ? "alert.removed" : "alert.not-found", "material", material.name());
    }

    private void list(CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        Map<String, Long> alerts = addon.alerts().list(player.getUniqueId());
        if (alerts.isEmpty()) {
            Lang.send(sender, "alert.empty");
            return;
        }
        sender.sendMessage(Messages.header(Lang.raw("alert.list-header")));
        for (Map.Entry<String, Long> entry : alerts.entrySet()) {
            Lang.send(sender, "alert.list-line", "material", entry.getKey(),
                    "price", Formatters.moneyFromCents(entry.getValue()));
        }
    }
}
