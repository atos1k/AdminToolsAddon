package dev.atos1k.auc.handler;

import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.CommandUtil;
import dev.atos1k.auc.util.Formatters;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.LotScanner;
import dev.atos1k.auc.util.Messages;
import dev.by1337.auc.auc.ClientAucLot;
import dev.by1337.auc.common.auc.log.LogQuery;
import dev.by1337.auc.common.auc.log.LogRecord;
import dev.by1337.auc.common.auc.log.impl.BuyAuctionLog;
import dev.by1337.auc.common.auc.log.impl.WithItemStackLog;
import dev.by1337.auc.common.auc.log.impl.WithLPriceLog;
import dev.by1337.auc.handler.Auction;
import dev.by1337.auc.handler.SimpleAuction;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

public class AnalyticsHandler {
    private final AdminToolsAddon addon;

    public AnalyticsHandler(AdminToolsAddon addon) {
        this.addon = addon;
    }

    public void handleItem(CommandSender sender, Auction auction, Material material, Integer hoursArg) {
        if (!sender.hasPermission(Permissions.ITEM)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (material == null) {
            sender.sendMessage(Lang.get("usage.item"));
            return;
        }
        int hours = hoursArg == null ? 24 * 7 : CommandUtil.clamp(hoursArg, 1, 24 * 90);
        int finalHours = hours;
        long after = System.currentTimeMillis() - hours * 3_600_000L;
        sender.sendMessage(Lang.get("item.collecting", "material", material.name()));

        SimpleAuction.WORKER.execute(() -> {
            List<ClientAucLot> lots = LotScanner.collectAllActive(auction);
            List<ClientAucLot> active = new ArrayList<>();
            Map<Integer, Material> knownItems = new HashMap<>();
            for (ClientAucLot lot : lots) {
                knownItems.put(lot.itemStack().id(), lot.itemStack().material());
                if (lot.itemStack().material() == material) active.add(lot);
            }
            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                LogQuery query = new LogQuery(null, null, after, null, null, null, BuyAuctionLog.ID, 3000);
                auction.loadLogs(query).then(records -> {
                    List<LogRecord> buys = records == null ? List.of() : records;
                    Set<Integer> unknown = new HashSet<>();
                    for (LogRecord r : buys) {
                        if (r.log() instanceof WithItemStackLog wi && !knownItems.containsKey(wi.item())) {
                            unknown.add(wi.item());
                        }
                    }
                    resolveItems(auction, new ArrayList<>(unknown), 0, knownItems,
                            () -> printItem(sender, material, finalHours, active, buys, knownItems));
                });
            });
        });
    }

    private void resolveItems(Auction auction, List<Integer> ids, int index,
                              Map<Integer, Material> out, Runnable done) {
        if (index >= ids.size()) {
            done.run();
            return;
        }
        int id = ids.get(index);
        auction.loadItem(id).then(item -> {
            if (item != null) out.put(id, item.material());
            resolveItems(auction, ids, index + 1, out, done);
        });
    }

    private void printItem(CommandSender sender, Material material, int hours,
                           List<ClientAucLot> active, List<LogRecord> buys, Map<Integer, Material> items) {
        long sales = 0;
        long soldItems = 0;
        long revenueCents = 0;
        Set<UUID> buyers = new HashSet<>();
        Set<UUID> sellers = new HashSet<>();
        for (LogRecord r : buys) {
            if (!(r.log() instanceof WithItemStackLog wi)) continue;
            if (items.get(wi.item()) != material) continue;
            sales++;
            soldItems += wi.count();
            if (r.log() instanceof WithLPriceLog wp) revenueCents += wp.lprice();
            if (r.actor() != null) buyers.add(r.actor());
            if (r.subject() != null) sellers.add(r.subject());
        }

        sender.sendMessage(Messages.header(Lang.rawFormatted("item.header", "material", material.name(), "hours", hours)));
        if (active.isEmpty()) {
            sender.sendMessage(Lang.get("item.no-active"));
        } else {
            long min = Long.MAX_VALUE;
            long max = 0;
            long sum = 0;
            long totalValue = 0;
            long count = 0;
            for (ClientAucLot lot : active) {
                long perOne = lot.lprice_for_one();
                min = Math.min(min, perOne);
                max = Math.max(max, perOne);
                sum += perOne;
                totalValue += lot.lprice();
                count += lot.count();
            }
            sender.sendMessage(Messages.kv(Lang.raw("item.active-lots"), active.size() + " (" + count + " шт.)"));
            sender.sendMessage(Messages.kv(Lang.raw("item.active-value"), Formatters.moneyFromCents(totalValue)));
            sender.sendMessage(Messages.kv(Lang.raw("item.ask-price"),
                    Formatters.moneyFromCents(min) + " / " + Formatters.moneyFromCents(sum / active.size())
                            + " / " + Formatters.moneyFromCents(max)));
        }

        if (sales == 0) {
            sender.sendMessage(Lang.get("item.no-sales"));
            return;
        }
        long avgSale = revenueCents / sales;
        sender.sendMessage(Messages.kv(Lang.raw("item.sales"), sales + " (" + soldItems + " шт.)"));
        sender.sendMessage(Messages.kv(Lang.raw("item.revenue"), Formatters.moneyFromCents(revenueCents)));
        sender.sendMessage(Messages.kv(Lang.raw("item.avg-sale"), Formatters.moneyFromCents(avgSale)));
        sender.sendMessage(Messages.kv(Lang.raw("item.traders"),
                buyers.size() + " / " + sellers.size()));
    }

    public void handleHours(CommandSender sender, Auction auction, Integer daysArg) {
        if (!sender.hasPermission(Permissions.HOURS)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        int days = daysArg == null ? 7 : CommandUtil.clamp(daysArg, 1, 30);
        int finalDays = days;
        long after = System.currentTimeMillis() - days * 86_400_000L;
        int scanLimit = 5000;
        sender.sendMessage(Lang.get("hours.collecting", "days", days));

        LogQuery query = new LogQuery(null, null, after, null, null, null, BuyAuctionLog.ID, scanLimit);
        auction.loadLogs(query).then(records -> {
            if (records == null || records.isEmpty()) {
                sender.sendMessage(Lang.get("activity.turnover-none"));
                return;
            }
            int[] counts = new int[24];
            long[] volume = new long[24];
            Calendar calendar = Calendar.getInstance();
            for (LogRecord r : records) {
                calendar.setTimeInMillis(r.timestamp());
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                counts[hour]++;
                if (r.log() instanceof WithLPriceLog p) volume[hour] += p.lprice();
            }
            int peak = 0;
            for (int c : counts) peak = Math.max(peak, c);

            sender.sendMessage(Messages.header(Lang.rawFormatted("hours.header", "days", finalDays)));
            for (int hour = 0; hour < 24; hour++) {
                int bars = peak == 0 ? 0 : Math.round(counts[hour] * 20F / peak);
                String label = String.format("%02d:00", hour);
                sender.sendMessage(Lang.get("hours.line", "hour", label, "bar", "█".repeat(bars),
                        "count", counts[hour], "volume", Formatters.moneyFromCents(volume[hour])));
            }
            if (records.size() == scanLimit) {
                sender.sendMessage(Lang.get("hours.truncated", "limit", scanLimit));
            }
        });
    }

    public void handleConcentration(CommandSender sender, Auction auction, Integer limitArg) {
        if (!sender.hasPermission(Permissions.CONCENTRATION)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        int finalLimit = limitArg == null ? 15 : CommandUtil.clamp(limitArg, 1, 50);
        sender.sendMessage(Lang.get("concentration.collecting"));

        SimpleAuction.WORKER.execute(() -> {
            List<ClientAucLot> lots = LotScanner.collectAllActive(auction);
            Map<Material, Map<UUID, Integer>> byMaterial = new HashMap<>();
            for (ClientAucLot lot : lots) {
                byMaterial.computeIfAbsent(lot.itemStack().material(), m -> new HashMap<>())
                        .merge(lot.owner(), 1, Integer::sum);
            }
            List<Object[]> rows = new ArrayList<>();
            for (Map.Entry<Material, Map<UUID, Integer>> e : byMaterial.entrySet()) {
                int total = 0;
                UUID topOwner = null;
                int topCount = 0;
                for (Map.Entry<UUID, Integer> owner : e.getValue().entrySet()) {
                    total += owner.getValue();
                    if (owner.getValue() > topCount) {
                        topCount = owner.getValue();
                        topOwner = owner.getKey();
                    }
                }
                if (total < 3) continue;
                double share = topCount * 100D / total;
                rows.add(new Object[]{e.getKey(), topOwner, topCount, total, share, e.getValue().size()});
            }
            rows.sort((a, b) -> Double.compare((double) b[4], (double) a[4]));
            List<Object[]> top = rows.subList(0, Math.min(finalLimit, rows.size()));

            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                if (top.isEmpty()) {
                    sender.sendMessage(Lang.get("concentration.empty"));
                    return;
                }
                sender.sendMessage(Messages.header(Lang.rawFormatted("concentration.header", "count", top.size())));
                printConcentration(sender, auction, top, 0, new HashMap<>());
            });
        });
    }

    private void printConcentration(CommandSender sender, Auction auction, List<Object[]> rows, int index,
                                    Map<UUID, String> cache) {
        if (index >= rows.size()) return;
        Object[] row = rows.get(index);
        UUID owner = (UUID) row[1];
        dev.atos1k.auc.util.NameResolver.resolve(auction, owner, cache, ownerName -> {
            sender.sendMessage(Lang.get("concentration.line",
                    "material", ((Material) row[0]).name(),
                    "share", Math.round((double) row[4]),
                    "owned", row[2], "total", row[3],
                    "owner", ownerName != null ? ownerName : owner.toString(),
                    "sellers", row[5]));
            printConcentration(sender, auction, rows, index + 1, cache);
        });
    }
}
