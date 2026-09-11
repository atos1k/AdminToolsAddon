package dev.atos1k.auc.command;

import dev.by1337.auc.BAuction;
import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.handler.ActivityHandler;
import dev.atos1k.auc.handler.AnnounceHandler;
import dev.atos1k.auc.handler.LotHandler;
import dev.atos1k.auc.handler.MarketHandler;
import dev.atos1k.auc.handler.PlayerHandler;
import dev.atos1k.auc.handler.TransactionsHandler;
import dev.atos1k.auc.handler.WatchHandler;
import dev.atos1k.auc.util.CommandUtil;
import dev.atos1k.auc.util.LogTypes;
import dev.atos1k.auc.util.Messages;
import dev.by1337.auc.handler.Auction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public class AdminCommand extends Command {

    private final TransactionsHandler transactionsHandler;
    private final MarketHandler marketHandler;
    private final ActivityHandler activityHandler;
    private final LotHandler lotHandler;
    private final PlayerHandler playerHandler;
    private final WatchHandler watchHandler;
    private final AnnounceHandler announceHandler;

    public AdminCommand(AdminToolsAddon addon) {
        super("baucadmin", "Расширенные админ-команды BAuction", "/baucadmin <sub>", List.of("aucadm", "aucadmin"));
        setPermission(Permissions.BASE);

        this.transactionsHandler = new TransactionsHandler(addon);
        this.marketHandler = new MarketHandler(addon);
        this.activityHandler = new ActivityHandler();
        this.lotHandler = new LotHandler(addon);
        this.playerHandler = new PlayerHandler(addon);
        this.watchHandler = new WatchHandler(addon);
        this.announceHandler = new AnnounceHandler();
    }

    public WatchHandler watchHandler() {
        return watchHandler;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(Permissions.BASE)) {
            sender.sendMessage(Messages.deny());
            return true;
        }
        Auction auction = BAuction.auction();
        if (auction == null) {
            sender.sendMessage(Messages.err("Аукцион ещё не готов, попробуйте чуть позже."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "transactions", "tx" -> transactionsHandler.handleTransactions(sender, auction, rest);
            case "itemhistory", "ihist" -> transactionsHandler.handleItemHistory(sender, auction, rest);
            case "export" -> transactionsHandler.handleExport(sender, auction, rest);
            case "liquid", "liquidity" -> marketHandler.handleLiquid(sender, auction, rest);
            case "stats" -> marketHandler.handleStats(sender, auction);
            case "price" -> marketHandler.handlePrice(sender, auction, rest);
            case "find" -> marketHandler.handleFind(sender, auction, rest);
            case "suspicious", "susp" -> marketHandler.handleSuspicious(sender, auction, rest);
            case "trend" -> activityHandler.handleTrend(sender, auction);
            case "turnover" -> activityHandler.handleTurnover(sender, auction, rest);
            case "top" -> activityHandler.handleTop(sender, auction, rest);
            case "lot" -> lotHandler.handleLot(sender, auction, rest);
            case "removelot", "rmlot" -> lotHandler.handleRemoveLot(sender, auction, rest);
            case "give", "compensate" -> lotHandler.handleGive(sender, auction, rest);
            case "player" -> playerHandler.handlePlayer(sender, auction, rest);
            case "wipe" -> playerHandler.handleWipe(sender, auction, rest);
            case "watch" -> watchHandler.handleWatch(sender);
            case "announce" -> announceHandler.handleAnnounce(sender, rest);
            default -> sendHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (!sender.hasPermission(Permissions.BASE)) return List.of();
        if (args.length == 1) {
            return CommandUtil.filterStartsWith(List.of("help", "transactions", "liquid", "stats", "lot", "removelot",
                    "player", "top", "price", "find", "watch", "suspicious", "turnover", "wipe",
                    "give", "trend", "export", "itemhistory", "announce"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String current = args[args.length - 1];
        int pos = args.length - 2;

        return switch (sub) {
            case "transactions", "tx" -> switch (pos) {
                case 0 -> CommandUtil.filterStartsWith(List.of("20", "50", "100"), current); 
                case 1 -> CommandUtil.filterStartsWith(CommandUtil.onlinePlayerNames(), current); 
                case 2 -> CommandUtil.filterStartsWith(new ArrayList<>(LogTypes.TYPE_ALIASES.keySet()), current); 
                case 3 -> CommandUtil.filterStartsWith(List.of("before:<id>"), current);
                default -> List.of();
            };
            case "export" -> switch (pos) {
                case 0 -> CommandUtil.filterStartsWith(List.of("24", "168", "720"), current); 
                case 1 -> CommandUtil.filterStartsWith(new ArrayList<>(LogTypes.TYPE_ALIASES.keySet()), current); 
                default -> List.of();
            };
            case "itemhistory", "ihist" -> switch (pos) {
                case 0 -> CommandUtil.filterStartsWith(List.of("<item_id>"), current);
                case 1 -> CommandUtil.filterStartsWith(List.of("24", "168", "720"), current); 
                case 2 -> CommandUtil.filterStartsWith(List.of("20", "50", "100"), current); 
                default -> List.of();
            };
            case "liquid", "liquidity" -> pos == 0
                    ? CommandUtil.filterStartsWith(List.of("15", "20", "50"), current) 
                    : List.of();
            case "price" -> pos == 0
                    ? CommandUtil.filterStartsWith(CommandUtil.materialNames(), current) 
                    : List.of();
            case "find" -> switch (pos) {
                case 0 -> CommandUtil.filterStartsWith(CommandUtil.materialNames(), current); 
                case 1 -> CommandUtil.filterStartsWith(List.of("20", "10", "50"), current); 
                default -> List.of();
            };
            case "suspicious", "susp" -> switch (pos) {
                case 0 -> CommandUtil.filterStartsWith(List.of("35", "50", "20"), current); 
                case 1 -> CommandUtil.filterStartsWith(List.of("20", "10", "50"), current); 
                default -> List.of();
            };
            case "trend" -> List.of();
            case "turnover" -> pos == 0
                    ? CommandUtil.filterStartsWith(List.of("24", "168", "720"), current) 
                    : List.of();
            case "top" -> switch (pos) {
                case 0 -> CommandUtil.filterStartsWith(List.of("buyers", "sellers"), current);
                case 1 -> CommandUtil.filterStartsWith(List.of("24", "168", "720"), current); 
                case 2 -> CommandUtil.filterStartsWith(List.of("10", "20", "50"), current); 
                default -> List.of();
            };
            case "lot", "removelot", "rmlot" -> pos == 0
                    ? CommandUtil.filterStartsWith(List.of("<id>"), current)
                    : List.of();
            case "give", "compensate" -> switch (pos) {
                case 0 -> CommandUtil.filterStartsWith(CommandUtil.onlinePlayerNames(), current); 
                case 1 -> CommandUtil.filterStartsWith(List.of("<цена>"), current); 
                case 2 -> CommandUtil.filterStartsWith(List.of("1", "16", "64"), current); 
                case 3 -> CommandUtil.filterStartsWith(List.of("24", "168", "720"), current); 
                default -> List.of();
            };
            case "player" -> pos == 0
                    ? CommandUtil.filterStartsWith(CommandUtil.onlinePlayerNames(), current) 
                    : List.of();
            case "wipe" -> switch (pos) {
                case 0 -> CommandUtil.filterStartsWith(CommandUtil.onlinePlayerNames(), current); 
                case 1 -> CommandUtil.filterStartsWith(List.of("confirm"), current);
                default -> List.of();
            };
            default -> List.of();
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Messages.header("BAuction AdminTools"));
        sendHelpLine(sender, "/baucadmin transactions [лимит] [ник] [тип] [before:<id>]", "журнал транзакций");
        sendHelpLine(sender, "/baucadmin itemhistory <item_id> [часы] [лимит]", "вся история конкретного предмета (id виден в transactions)");
        sendHelpLine(sender, "/baucadmin export [часы] [тип]", "выгрузить транзакции в CSV-файл на диск");
        sendHelpLine(sender, "/baucadmin liquid [лимит]", "самые ликвидные товары (по числу активных лотов)");
        sendHelpLine(sender, "/baucadmin stats", "общая статистика аукциона");
        sendHelpLine(sender, "/baucadmin price <материал>", "рыночная цена по активным лотам (мин/среднее/макс)");
        sendHelpLine(sender, "/baucadmin find <материал> [лимит]", "все активные лоты с этим предметом");
        sendHelpLine(sender, "/baucadmin suspicious [порог%] [лимит]", "лоты, выставленные подозрительно дёшево");
        sendHelpLine(sender, "/baucadmin trend", "активность аукциона: 1ч / 24ч / 7д в сравнении");
        sendHelpLine(sender, "/baucadmin turnover [часы]", "оборот аукциона (сумма покупок) за период");
        sendHelpLine(sender, "/baucadmin top [buyers|sellers] [часы] [лимит]", "топ по обороту за период");
        sendHelpLine(sender, "/baucadmin lot <id>", "информация о лоте");
        sendHelpLine(sender, "/baucadmin removelot <id>", "снять лот, вернуть предмет владельцу в vault");
        sendHelpLine(sender, "/baucadmin give <ник> <цена> [кол-во] [часы]", "выставить предмет из руки лотом для игрока (компенсации, ивенты)");
        sendHelpLine(sender, "/baucadmin player <ник>", "торговый профиль игрока (сколько купил/продал, vault)");
        sendHelpLine(sender, "/baucadmin wipe <ник> confirm", "снять ВСЕ лоты игрока в его vault (массово)");
        sendHelpLine(sender, "/baucadmin watch", "включить/выключить живую ленту транзакций себе в чат");
        sendHelpLine(sender, "/baucadmin announce <текст>", "разослать объявление всем игрокам от лица аукциона");
        sender.sendMessage(Component.text("Типы транзакций: " + String.join(", ", LogTypes.TYPE_ALIASES.keySet()), NamedTextColor.GRAY));
    }

    private void sendHelpLine(CommandSender sender, String usage, String desc) {
        sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW)
                .append(Component.text(" — " + desc, NamedTextColor.GRAY)));
    }
}