package dev.atos1k.auc.command;

import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.args.ArgumentMaterial;
import dev.atos1k.auc.command.args.ArgumentPlaceholder;
import dev.atos1k.auc.command.args.ArgumentPlayerName;
import dev.atos1k.auc.handler.ActivityHandler;
import dev.atos1k.auc.handler.AnalyticsHandler;
import dev.atos1k.auc.handler.AnnounceHandler;
import dev.atos1k.auc.handler.BanHandler;
import dev.atos1k.auc.handler.BlacklistHandler;
import dev.atos1k.auc.handler.FraudHandler;
import dev.atos1k.auc.handler.LotHandler;
import dev.atos1k.auc.handler.MarketHandler;
import dev.atos1k.auc.handler.PlayerHandler;
import dev.atos1k.auc.handler.TransactionsHandler;
import dev.atos1k.auc.handler.WatchHandler;
import dev.atos1k.auc.util.BanTypes;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.LogTypes;
import dev.atos1k.auc.util.Messages;
import dev.by1337.auc.BAuction;
import dev.by1337.auc.handler.Auction;
import dev.by1337.cmd.Command;
import dev.by1337.cmd.CommandMsgError;
import dev.by1337.cmd.Requires;
import dev.by1337.cmd.argument.ArgumentStrings;
import dev.by1337.core.command.bcmd.argument.ArgumentChoice;
import dev.by1337.core.command.bcmd.argument.ArgumentDouble;
import dev.by1337.core.command.bcmd.argument.ArgumentInt;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public final class AdminCommands {
    private AdminCommands() {
    }

    public static Command<CommandSender> install(AdminToolsAddon addon, Command<CommandSender> cmd) {
        TransactionsHandler transactions = new TransactionsHandler(addon);
        MarketHandler market = new MarketHandler(addon);
        ActivityHandler activity = new ActivityHandler();
        AnalyticsHandler analytics = new AnalyticsHandler(addon);
        FraudHandler fraud = new FraudHandler(addon);
        LotHandler lot = new LotHandler(addon);
        PlayerHandler player = new PlayerHandler(addon);
        WatchHandler watch = addon.watchHandler();
        AnnounceHandler announce = new AnnounceHandler();
        BanHandler ban = new BanHandler(addon);
        BlacklistHandler blacklist = new BlacklistHandler(addon);

        Requires<CommandSender> admin = permission(Permissions.BASE);

        cmd.sub(new Command<CommandSender>("help").alias("ahelp").requires(admin).executor(
                AdminCommands::sendHelp));

        cmd.sub(new Command<CommandSender>("transactions").alias("tx").requires(permission(Permissions.TRANSACTIONS)).executor(
                new ArgumentInt<>("limit"),
                new ArgumentPlayerName<>("player"),
                new ArgumentChoice<>("type", new ArrayList<>(LogTypes.TYPE_ALIASES.keySet())),
                new ArgumentPlaceholder<>("before", "before:<id>"),
                (s, limit, name, type, before) -> transactions.handleTransactions(s, auction(s), limit, name, type, before)));

        cmd.sub(new Command<CommandSender>("itemhistory").alias("ihist").requires(permission(Permissions.ITEMHISTORY)).executor(
                new ArgumentInt<>("item_id"),
                new ArgumentInt<>("hours"),
                new ArgumentInt<>("limit"),
                (s, itemId, hours, limit) -> transactions.handleItemHistory(s, auction(s), itemId, hours, limit)));

        cmd.sub(new Command<CommandSender>("export").requires(permission(Permissions.EXPORT)).executor(
                new ArgumentInt<>("hours"),
                new ArgumentChoice<>("type", new ArrayList<>(LogTypes.TYPE_ALIASES.keySet())),
                (s, hours, type) -> transactions.handleExport(s, auction(s), hours, type)));

        cmd.sub(new Command<CommandSender>("liquid").requires(permission(Permissions.LIQUID)).executor(
                new ArgumentInt<>("limit"),
                (s, limit) -> market.handleLiquid(s, auction(s), limit)));

        cmd.sub(new Command<CommandSender>("stats").alias("statistic").requires(permission(Permissions.STATS)).executor(
                s -> market.handleStats(s, auction(s))));

        cmd.sub(new Command<CommandSender>("price").requires(permission(Permissions.PRICE)).executor(
                new ArgumentMaterial<>("material"),
                (s, material) -> market.handlePrice(s, auction(s), material)));

        cmd.sub(new Command<CommandSender>("find").requires(permission(Permissions.FIND)).executor(
                new ArgumentMaterial<>("material"),
                new ArgumentInt<>("limit"),
                (s, material, limit) -> market.handleFind(s, auction(s), material, limit)));

        cmd.sub(new Command<CommandSender>("suspicious").alias("susp").requires(permission(Permissions.SUSPICIOUS)).executor(
                new ArgumentInt<>("threshold"),
                new ArgumentInt<>("limit"),
                (s, threshold, limit) -> market.handleSuspicious(s, auction(s), threshold, limit)));

        cmd.sub(new Command<CommandSender>("item").requires(permission(Permissions.ITEM)).executor(
                new ArgumentMaterial<>("material"),
                new ArgumentInt<>("hours"),
                (s, material, hours) -> analytics.handleItem(s, auction(s), material, hours)));

        cmd.sub(new Command<CommandSender>("hours").alias("activity").requires(permission(Permissions.HOURS)).executor(
                new ArgumentInt<>("days"),
                (s, days) -> analytics.handleHours(s, auction(s), days)));

        cmd.sub(new Command<CommandSender>("concentration").alias("monopoly").requires(permission(Permissions.CONCENTRATION)).executor(
                new ArgumentInt<>("limit"),
                (s, limit) -> analytics.handleConcentration(s, auction(s), limit)));

        cmd.sub(new Command<CommandSender>("laundering").requires(permission(Permissions.LAUNDERING)).executor(
                new ArgumentInt<>("hours"),
                new ArgumentInt<>("limit"),
                (s, hours, limit) -> fraud.handleLaundering(s, auction(s), hours, limit)));

        cmd.sub(new Command<CommandSender>("overpriced").requires(permission(Permissions.OVERPRICED)).executor(
                new ArgumentInt<>("hours"),
                new ArgumentInt<>("threshold"),
                new ArgumentInt<>("limit"),
                (s, hours, threshold, limit) -> fraud.handleOverpriced(s, auction(s), hours, threshold, limit)));

        cmd.sub(new Command<CommandSender>("trend").requires(permission(Permissions.TREND)).executor(
                s -> activity.handleTrend(s, auction(s))));

        cmd.sub(new Command<CommandSender>("turnover").requires(permission(Permissions.TURNOVER)).executor(
                new ArgumentInt<>("hours"),
                (s, hours) -> activity.handleTurnover(s, auction(s), hours)));

        cmd.sub(new Command<CommandSender>("top").requires(permission(Permissions.TOP)).executor(
                new ArgumentChoice<>("side", List.of("buyers", "sellers")),
                new ArgumentInt<>("hours"),
                new ArgumentInt<>("limit"),
                (s, side, hours, limit) -> activity.handleTop(s, auction(s), side, hours, limit)));

        cmd.sub(new Command<CommandSender>("lot").requires(permission(Permissions.LOT)).executor(
                new ArgumentInt<>("id"),
                (s, id) -> lot.handleLot(s, auction(s), id)));

        cmd.sub(new Command<CommandSender>("removelot").alias("rmlot").requires(permission(Permissions.REMOVELOT)).executor(
                new ArgumentInt<>("id"),
                (s, id) -> lot.handleRemoveLot(s, auction(s), id)));

        cmd.sub(new Command<CommandSender>("give").requires(permission(Permissions.GIVE)).executor(
                new ArgumentPlayerName<>("player"),
                new ArgumentDouble<>("price"),
                new ArgumentInt<>("count"),
                new ArgumentInt<>("hours"),
                (s, name, price, count, hours) -> lot.handleGive(s, auction(s), name, price, count, hours)));

        cmd.sub(new Command<CommandSender>("player").requires(permission(Permissions.PLAYER)).executor(
                new ArgumentPlayerName<>("player"),
                (s, name) -> player.handlePlayer(s, auction(s), name)));

        cmd.sub(new Command<CommandSender>("wipe").requires(permission(Permissions.WIPE)).executor(
                new ArgumentPlayerName<>("player"),
                new ArgumentChoice<>("confirm", List.of("confirm")),
                (s, name, confirm) -> player.handleWipe(s, auction(s), name, confirm)));

        cmd.sub(new Command<CommandSender>("watch").requires(permission(Permissions.WATCH))
                .sub(new Command<CommandSender>("player").executor(
                        new ArgumentPlayerName<>("player"),
                        (s, name) -> watch.handleWatchPlayer(s, auction(s), name)))
                .sub(new Command<CommandSender>("type").executor(
                        new ArgumentChoice<>("type", new ArrayList<>(LogTypes.TYPE_ALIASES.keySet())), watch::handleWatchType))
                .sub(new Command<CommandSender>("min").executor(
                        new ArgumentDouble<>("price"), watch::handleWatchMin))
                .sub(new Command<CommandSender>("status").executor(watch::handleWatchStatus))
                .sub(new Command<CommandSender>("clear").executor(watch::handleWatchClear))
                .sub(new Command<CommandSender>("off").executor(watch::handleWatchOff))
                .executor(
                        new ArgumentChoice<>("type", new ArrayList<>(LogTypes.TYPE_ALIASES.keySet())), watch::handleWatch));

        cmd.sub(new Command<CommandSender>("ban").requires(permission(Permissions.BAN)).executor(
                new ArgumentPlayerName<>("player"),
                new ArgumentChoice<>("type", BanTypes.ids()),
                new ArgumentChoice<>("duration", List.of("perm", "1h", "12h", "7d", "30d")),
                new ArgumentStrings<>("reason"),
                (s, name, type, duration, reason) -> ban.handleBan(s, auction(s), name, type, duration, reason)));

        cmd.sub(new Command<CommandSender>("unban").requires(permission(Permissions.BAN)).executor(
                new ArgumentPlayerName<>("player"),
                new ArgumentChoice<>("type", BanTypes.ids()),
                (s, name, type) -> ban.handleUnban(s, auction(s), name, type)));

        cmd.sub(new Command<CommandSender>("bans").alias("banlist").requires(permission(Permissions.BAN)).executor(
                ban::handleBans));

        cmd.sub(new Command<CommandSender>("blacklist").alias("bl").requires(permission(Permissions.BLACKLIST))
                .sub(new Command<CommandSender>("add").executor(
                        new ArgumentMaterial<>("material"), blacklist::handleAdd))
                .sub(new Command<CommandSender>("hand").executor(blacklist::handleHand))
                .sub(new Command<CommandSender>("tag").executor(
                        new ArgumentPlaceholder<>("tag", "<тег>"), blacklist::handleTag))
                .sub(new Command<CommandSender>("handtags").alias("tags").executor(blacklist::handleHandTags))
                .sub(new Command<CommandSender>("remove").alias("rm").executor(
                        new ArgumentChoice<>("value", blacklist::listedValues), blacklist::handleRemove))
                .sub(new Command<CommandSender>("list").executor(blacklist::handleList))
                .executor(blacklist::handleList));

        cmd.sub(new Command<CommandSender>("announce").alias("broadcast").requires(permission(Permissions.ANNOUNCE)).executor(
                new ArgumentStrings<>("text"), announce::handleAnnounce));

        cmd.executor(sender -> {
            if (!sender.hasPermission(Permissions.BASE)) {
                sender.sendMessage(Messages.deny());
                return;
            }
            sendHelp(sender);
        });

        return cmd;
    }

    private static Requires<CommandSender> permission(String node) {
        return sender -> sender.hasPermission(node);
    }

    private static Auction auction(CommandSender sender) {
        Auction auction = BAuction.auction();
        if (auction == null) throw new CommandMsgError(Lang.raw("general.auction-not-ready"));
        return auction;
    }

    private static void sendHelp(CommandSender sender) {
        sender.sendMessage(Messages.header(Lang.raw("help.header")));
        for (Component line : Lang.getList("help.lines")) {
            sender.sendMessage(line);
        }
        sender.sendMessage(Lang.get("help.types-line", "types", String.join(", ", LogTypes.TYPE_ALIASES.keySet())));
    }
}
