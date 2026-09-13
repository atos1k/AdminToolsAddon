package dev.atos1k.auc.handler;

import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.CommandUtil;
import dev.atos1k.auc.util.Formatters;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.Messages;
import dev.by1337.auc.common.auc.log.LogQuery;
import dev.by1337.auc.common.auc.log.LogRecord;
import dev.by1337.auc.common.auc.log.impl.BuyAuctionLog;
import dev.by1337.auc.common.auc.log.impl.WithLPriceLog;
import dev.by1337.auc.handler.Auction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public class ActivityHandler {
    private record Volume(int count, long sumCents) {
    }

    public void handleTrend(CommandSender sender, Auction auction) {
        if (!sender.hasPermission(Permissions.TREND)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        sender.sendMessage(Messages.info("Считаю активность аукциона..."));
        long now = System.currentTimeMillis();
        fetchWindowVolume(auction, now - 3_600_000L, v1 ->
                fetchWindowVolume(auction, now - 24 * 3_600_000L, v24 ->
                        fetchWindowVolume(auction, now - 7L * 24 * 3_600_000L, v7 -> {
                            sender.sendMessage(Messages.header("Активность аукциона (покупки)"));
                            sender.sendMessage(trendLine("Последний час", v1));
                            sender.sendMessage(trendLine("Последние 24 часа", v24));
                            sender.sendMessage(trendLine("Последние 7 дней", v7));
                        })));
    }

    private void fetchWindowVolume(Auction auction, long after, Consumer<Volume> then) {
        LogQuery q = new LogQuery(null, null, after, null, null, null, BuyAuctionLog.ID, 2000);
        auction.loadLogs(q).then(records -> {
            int count = records == null ? 0 : records.size();
            long sum = 0;
            if (records != null) {
                for (LogRecord r : records) {
                    if (r.log() instanceof WithLPriceLog p) sum += p.lprice();
                }
            }
            then.accept(new Volume(count, sum));
        });
    }

    private Component trendLine(String label, Volume v) {
        return Lang.get("activity.trend-line", "label", label, "count", v.count(),
                "volume", Formatters.moneyFromCents(v.sumCents()));
    }

    public void handleTurnover(CommandSender sender, Auction auction, Integer hoursArg) {
        if (!sender.hasPermission(Permissions.TURNOVER)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        int finalHours = hoursArg == null ? 24 : CommandUtil.clamp(hoursArg, 1, 24 * 30);
        int hours = finalHours;
        long afterTimestamp = System.currentTimeMillis() - hours * 3_600_000L;
        int scanLimit = 2000;

        LogQuery query = new LogQuery(null, null, afterTimestamp, null, null, null, BuyAuctionLog.ID, scanLimit);
        sender.sendMessage(Messages.info("Считаю оборот за последние " + finalHours + " ч..."));
        auction.loadLogs(query).then(records -> {
            if (records == null || records.isEmpty()) {
                sender.sendMessage(Lang.get("activity.turnover-none"));
                return;
            }
            long totalCents = 0;
            Set<UUID> buyers = new HashSet<>();
            Set<UUID> sellers = new HashSet<>();
            for (LogRecord r : records) {
                if (r.log() instanceof WithLPriceLog p) totalCents += p.lprice();
                if (r.actor() != null) buyers.add(r.actor());
                if (r.subject() != null) sellers.add(r.subject());
            }
            sender.sendMessage(Messages.header("Оборот аукциона за " + finalHours + " ч."));
            sender.sendMessage(Messages.kv("Сделок", String.valueOf(records.size())));
            sender.sendMessage(Messages.kv("Суммарный оборот", Formatters.moneyFromCents(totalCents)));
            sender.sendMessage(Messages.kv("Уникальных покупателей", String.valueOf(buyers.size())));
            sender.sendMessage(Messages.kv("Уникальных продавцов", String.valueOf(sellers.size())));
            if (records.size() == scanLimit) {
                sender.sendMessage(Lang.get("activity.truncated", "limit", scanLimit));
            }
        });
    }
    
    public void handleTop(CommandSender sender, Auction auction, String side, Integer hoursArg, Integer limitArg) {
        if (!sender.hasPermission(Permissions.TOP)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        boolean finalByBuyers = side == null || !side.equalsIgnoreCase("sellers");
        int finalHours = hoursArg == null ? 24 : CommandUtil.clamp(hoursArg, 1, 24 * 30);
        int finalLeaderboardSize = limitArg == null ? 10 : CommandUtil.clamp(limitArg, 1, 50);
        long afterTimestamp = System.currentTimeMillis() - finalHours * 3_600_000L;
        int scanLimit = 1000;

        LogQuery query = new LogQuery(null, null, afterTimestamp, null, null, null, BuyAuctionLog.ID, scanLimit);
        sender.sendMessage(Messages.info("Считаю топ за последние " + finalHours + " ч. (по последним до " + scanLimit + " покупкам)..."));
        auction.loadLogs(query).then(records -> {
            if (records == null || records.isEmpty()) {
                sender.sendMessage(Lang.get("activity.top-none"));
                return;
            }
            Map<UUID, long[]> agg = new HashMap<>();
            for (LogRecord r : records) {
                UUID key = finalByBuyers ? r.actor() : r.subject();
                if (key == null) continue;
                long price = r.log() instanceof WithLPriceLog p ? p.lprice() : 0;
                long[] entry = agg.computeIfAbsent(key, k -> new long[2]);
                entry[0] += price;
                entry[1] += 1;
            }
            List<Map.Entry<UUID, long[]>> sorted = new ArrayList<>(agg.entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
            List<Map.Entry<UUID, long[]>> top = sorted.subList(0, Math.min(finalLeaderboardSize, sorted.size()));

            sender.sendMessage(Messages.header("Топ " + (finalByBuyers ? "покупателей" : "продавцов") + " за " + finalHours + " ч."));
            printTopEntries(sender, auction, top, 0, 1);
            if (records.size() == scanLimit) {
                sender.sendMessage(Lang.get("activity.truncated", "limit", scanLimit));
            }
        });
    }

    private void printTopEntries(CommandSender sender, Auction auction, List<Map.Entry<UUID, long[]>> entries, int index, int rank) {
        if (index >= entries.size()) return;
        var e = entries.get(index);
        auction.loadName(e.getKey()).then(pn -> {
            String name = pn != null ? pn.name() : e.getKey().toString();
            long totalCents = e.getValue()[0];
            long count = e.getValue()[1];
            sender.sendMessage(Lang.get("activity.top-line", "rank", rank, "player", name,
                    "volume", Formatters.moneyFromCents(totalCents), "count", count));
            printTopEntries(sender, auction, entries, index + 1, rank + 1);
        });
    }
}
