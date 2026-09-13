package dev.atos1k.auc.handler;

import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.CommandUtil;
import dev.atos1k.auc.util.Formatters;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.LogTypes;
import dev.atos1k.auc.util.Messages;
import dev.atos1k.auc.util.NameResolver;
import dev.by1337.auc.common.auc.log.AuctionLog;
import dev.by1337.auc.common.auc.log.LogQuery;
import dev.by1337.auc.common.auc.log.LogRecord;
import dev.by1337.auc.common.auc.log.impl.WithItemStackLog;
import dev.by1337.auc.common.auc.log.impl.WithLPriceLog;
import dev.by1337.auc.handler.Auction;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.CommandSender;

public class TransactionsHandler {
    private final AdminToolsAddon addon;

    public TransactionsHandler(AdminToolsAddon addon) {
        this.addon = addon;
    }
    
    public void handleTransactions(CommandSender sender, Auction auction, Integer limitArg, String playerName,
                                   String typeAlias, String before) {
        if (!sender.hasPermission(Permissions.TRANSACTIONS)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        int finalLimit = limitArg == null ? 20 : CommandUtil.clamp(limitArg, 1, 100);
        String finalType = typeAlias == null ? null : LogTypes.TYPE_ALIASES.get(typeAlias.toLowerCase(Locale.ROOT));
        Long finalBeforeId = before == null ? null
                : CommandUtil.parseLongOrNull(before.toLowerCase(Locale.ROOT).startsWith("before:") ? before.substring(7) : before);
        if (playerName != null) {
            String finalPlayerName = playerName;
            auction.findUUID(playerName).then(pair -> {
                if (pair == null) {
                    sender.sendMessage(Lang.get("general.player-not-found", "player", finalPlayerName));
                    return;
                }
                queryTransactions(sender, auction, pair.getKey(), finalType, finalLimit, finalBeforeId);
            });
        } else {
            queryTransactions(sender, auction, null, finalType, finalLimit, finalBeforeId);
        }
    }

    private void queryTransactions(CommandSender sender, Auction auction, UUID actor, String type, int limit, Long beforeId) {
        LogQuery query = new LogQuery(null, beforeId, null, null, actor, null, type, limit);
        auction.loadLogs(query).then(records -> {
            if (records == null || records.isEmpty()) {
                sender.sendMessage(Lang.get("transactions.none-found"));
                return;
            }
            sender.sendMessage(Messages.header("Транзакции (" + records.size() + ")"));
            resolveAndPrint(sender, auction, records, 0, new HashMap<>());
        });
    }

    void resolveAndPrint(CommandSender sender, Auction auction, List<LogRecord> records, int index, Map<UUID, String> nameCache) {
        if (index >= records.size()) {
            long minId = records.get(records.size() - 1).uid();
            sender.sendMessage(Lang.get("transactions.before-hint", "id", minId));
            return;
        }
        LogRecord record = records.get(index);
        AuctionLog log = record.log();

        NameResolver.resolve(auction, record.actor(), nameCache, actorName ->
                NameResolver.resolve(auction, record.subject(), nameCache, subjectName -> {
                    if (log instanceof WithItemStackLog withItem) {
                        auction.loadItem(withItem.item()).then(item -> {
                            String itemName = item != null ? item.itemNameNoColors() : "неизвестный предмет";
                            String priceStr = log instanceof WithLPriceLog withPrice
                                    ? Formatters.moneyFromCents(withPrice.lprice()) : "-";
                            sender.sendMessage(Messages.txLine(record, log, actorName, subjectName, itemName, withItem.item(), withItem.count(), priceStr));
                            resolveAndPrint(sender, auction, records, index + 1, nameCache);
                        });
                    } else {
                        sender.sendMessage(Messages.txLine(record, log, actorName, subjectName, "-", null, 0, "-"));
                        resolveAndPrint(sender, auction, records, index + 1, nameCache);
                    }
                }));
    }
    
    public void handleItemHistory(CommandSender sender, Auction auction, Integer itemIdArg, Integer hoursArg, Integer limitArg) {
        if (!sender.hasPermission(Permissions.ITEMHISTORY)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (itemIdArg == null) {
            sender.sendMessage(Lang.get("usage.itemhistory"));
            return;
        }
        int itemId = itemIdArg;
        int hours = hoursArg == null ? 24 * 7 : CommandUtil.clamp(hoursArg, 1, 24 * 90);
        long after = System.currentTimeMillis() - hours * 3_600_000L;
        int finalLimit = limitArg == null ? 20 : CommandUtil.clamp(limitArg, 1, 100);
        int scanLimit = 3000;

        LogQuery query = new LogQuery(null, null, after, null, null, null, null, scanLimit);
        sender.sendMessage(Messages.info("Ищу историю предмета #" + itemId + "..."));
        auction.loadLogs(query).then(records -> {
            if (records == null || records.isEmpty()) {
                sender.sendMessage(Lang.get("itemhistory.none-in-period"));
                return;
            }
            List<LogRecord> matching = new ArrayList<>();
            for (LogRecord r : records) {
                if (r.log() instanceof WithItemStackLog wi && wi.item() == itemId) {
                    matching.add(r);
                    if (matching.size() >= finalLimit) break;
                }
            }
            if (matching.isEmpty()) {
                sender.sendMessage(Lang.get("itemhistory.none-for-item", "id", itemId));
                return;
            }
            sender.sendMessage(Messages.header("История предмета #" + itemId + " (" + matching.size() + ")"));
            resolveAndPrint(sender, auction, matching, 0, new HashMap<>());
        });
    }
    
    public void handleExport(CommandSender sender, Auction auction, Integer hoursArg, String typeAlias) {
        if (!sender.hasPermission(Permissions.EXPORT)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        int hours = hoursArg == null ? 24 : CommandUtil.clamp(hoursArg, 1, 24 * 90);
        String type = typeAlias == null ? null : LogTypes.TYPE_ALIASES.get(typeAlias.toLowerCase(Locale.ROOT));
        int finalHours = hours;
        long after = System.currentTimeMillis() - hours * 3_600_000L;
        int scanLimit = 5000;
        LogQuery query = new LogQuery(null, null, after, null, null, null, type, scanLimit);
        sender.sendMessage(Messages.info("Выгружаю транзакции за последние " + finalHours + " ч..."));
        auction.loadLogs(query).then(records -> {
            if (records == null || records.isEmpty()) {
                sender.sendMessage(Lang.get("export.nothing"));
                return;
            }
            exportRow(sender, auction, records, 0, new HashMap<>(), new ArrayList<>(), records.size() == scanLimit);
        });
    }

    private void exportRow(CommandSender sender, Auction auction, List<LogRecord> records, int index,
                            Map<UUID, String> cache, List<String[]> rows, boolean truncated) {
        if (index >= records.size()) {
            writeCsv(sender, rows, truncated);
            return;
        }
        LogRecord r = records.get(index);
        NameResolver.resolve(auction, r.actor(), cache, actorName ->
                NameResolver.resolve(auction, r.subject(), cache, subjectName -> {
                    AuctionLog log = r.log();
                    rows.add(new String[]{
                            String.valueOf(r.uid()),
                            Formatters.date(r.timestamp()),
                            LogTypes.describe(log.type()),
                            actorName != null ? actorName : "",
                            subjectName != null ? subjectName : "",
                            log instanceof WithItemStackLog wi ? String.valueOf(wi.item()) : "",
                            log instanceof WithItemStackLog wi ? String.valueOf(wi.count()) : "",
                            log instanceof WithLPriceLog wp ? Formatters.moneyFromCents(wp.lprice()) : ""
                    });
                    exportRow(sender, auction, records, index + 1, cache, rows, truncated);
                }));
    }

    private void writeCsv(CommandSender sender, List<String[]> rows, boolean truncated) {
        File dir = new File(addon.dataFolder(), "exports");
        if (!dir.exists() && !dir.mkdirs()) {
            sender.sendMessage(Lang.get("export.folder-error", "path", dir.getPath()));
            return;
        }
        File file = new File(dir, "transactions_" + System.currentTimeMillis() + ".csv");
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            writer.println("uid;time;type;actor;subject;item_id;count;price");
            for (String[] row : rows) {
                writer.println(String.join(";", row));
            }
        } catch (IOException e) {
            sender.sendMessage(Lang.get("export.write-error", "error", e.getMessage()));
            return;
        }
        String truncatedSuffix = truncated ? Lang.rawFormatted("export.truncated-suffix") : "";
        sender.sendMessage(Lang.get("export.done", "count", rows.size(), "path", file.getPath(), "truncated", truncatedSuffix));
    }
}
