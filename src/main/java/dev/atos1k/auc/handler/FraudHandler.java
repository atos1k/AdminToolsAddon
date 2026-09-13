package dev.atos1k.auc.handler;

import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.CommandUtil;
import dev.atos1k.auc.util.Formatters;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.Messages;
import dev.atos1k.auc.util.NameResolver;
import dev.by1337.auc.common.auc.log.LogQuery;
import dev.by1337.auc.common.auc.log.LogRecord;
import dev.by1337.auc.common.auc.log.impl.BuyAuctionLog;
import dev.atos1k.auc.util.LotScanner;
import dev.by1337.auc.auc.ClientAucLot;
import dev.by1337.auc.common.auc.log.impl.WithItemStackLog;
import dev.by1337.auc.common.auc.log.impl.WithLPriceLog;
import dev.by1337.auc.handler.Auction;
import dev.by1337.auc.handler.SimpleAuction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

public class FraudHandler {
    private final AdminToolsAddon addon;
    public FraudHandler(AdminToolsAddon addon) {
        this.addon = addon;
    }

    public void handleLaundering(CommandSender sender, Auction auction, Integer hoursArg, Integer limitArg) {
        if (!sender.hasPermission(Permissions.LAUNDERING)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        int hours = hoursArg == null ? 24 * 7 : CommandUtil.clamp(hoursArg, 1, 24 * 90);
        int finalHours = hours;
        int finalLimit = limitArg == null ? 15 : CommandUtil.clamp(limitArg, 1, 50);
        long after = System.currentTimeMillis() - hours * 3_600_000L;
        int scanLimit = 5000;
        sender.sendMessage(Lang.get("laundering.collecting", "hours", hours));

        LogQuery query = new LogQuery(null, null, after, null, null, null, BuyAuctionLog.ID, scanLimit);
        auction.loadLogs(query).then(records -> {
            if (records == null || records.isEmpty()) {
                sender.sendMessage(Lang.get("activity.turnover-none"));
                return;
            }
            Map<String, Pair> pairs = new HashMap<>();
            for (LogRecord r : records) {
                UUID buyer = r.actor();
                UUID seller = r.subject();
                if (buyer == null || seller == null || buyer.equals(seller)) continue;
                boolean forward = buyer.compareTo(seller) < 0;
                UUID a = forward ? buyer : seller;
                UUID b = forward ? seller : buyer;
                long price = r.log() instanceof WithLPriceLog p ? p.lprice() : 0;
                Pair pair = pairs.computeIfAbsent(a + ":" + b, k -> new Pair(a, b));
                if (forward) {
                    pair.aToB++;
                } else {
                    pair.bToA++;
                }
                pair.totalCents += price;
            }
            List<Pair> flagged = new ArrayList<>();
            for (Pair pair : pairs.values()) {
                if (pair.aToB > 0 && pair.bToA > 0) flagged.add(pair);
            }
            flagged.sort((x, y) -> Long.compare(y.totalCents, x.totalCents));
            List<Pair> top = flagged.subList(0, Math.min(finalLimit, flagged.size()));

            if (top.isEmpty()) {
                sender.sendMessage(Lang.get("laundering.empty", "hours", finalHours));
                return;
            }
            sender.sendMessage(Messages.header(Lang.rawFormatted("laundering.header",
                    "count", top.size(), "hours", finalHours)));
            printPairs(sender, auction, top, 0, new HashMap<>());
            if (records.size() == scanLimit) {
                sender.sendMessage(Lang.get("laundering.truncated", "limit", scanLimit));
            }
        });
    }

    public void handleOverpriced(CommandSender sender, Auction auction, Integer hoursArg, Integer thresholdArg, Integer limitArg) {
        if (!sender.hasPermission(Permissions.OVERPRICED)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        int hours = hoursArg == null ? 24 * 7 : CommandUtil.clamp(hoursArg, 1, 24 * 90);
        int threshold = thresholdArg == null ? 300 : CommandUtil.clamp(thresholdArg, 110, 100000);
        int limit = limitArg == null ? 15 : CommandUtil.clamp(limitArg, 1, 50);
        long after = System.currentTimeMillis() - hours * 3_600_000L;
        int scanLimit = 5000;
        sender.sendMessage(Lang.get("overpriced.collecting", "hours", hours, "threshold", threshold));

        LogQuery query = new LogQuery(null, null, after, null, null, null, BuyAuctionLog.ID, scanLimit);
        auction.loadLogs(query).then(records -> {
            if (records == null || records.isEmpty()) {
                sender.sendMessage(Lang.get("activity.turnover-none"));
                return;
            }
            SimpleAuction.WORKER.execute(() -> {
                Map<Integer, Material> items = new HashMap<>();
                Map<Material, List<Long>> reference = new HashMap<>();
                for (ClientAucLot lot : LotScanner.collectAllActive(auction)) {
                    items.put(lot.itemStack().id(), lot.itemStack().material());
                    reference.computeIfAbsent(lot.itemStack().material(), m -> new ArrayList<>()).add(lot.lprice_for_one());
                }
                Set<Integer> unknown = new HashSet<>();
                for (LogRecord r : records) {
                    if (r.log() instanceof WithItemStackLog wi && !items.containsKey(wi.item())) unknown.add(wi.item());
                }
                Bukkit.getScheduler().runTask(addon.getPlugin(), () -> resolveItems(auction, new ArrayList<>(unknown), 0, items,
                        () -> printOverpriced(sender, auction, records, items, reference, threshold, limit, hours,
                                records.size() == scanLimit)));
            });
        });
    }

    private void resolveItems(Auction auction, List<Integer> ids, int index, Map<Integer, Material> out, Runnable done) {
        if (index >= ids.size()) {
            done.run();
            return;
        }
        auction.loadItem(ids.get(index)).then(item -> {
            if (item != null) out.put(ids.get(index), item.material());
            resolveItems(auction, ids, index + 1, out, done);
        });
    }

    private void printOverpriced(CommandSender sender, Auction auction, List<LogRecord> records,
                                 Map<Integer, Material> items, Map<Material, List<Long>> reference,
                                 int threshold, int limit, int hours, boolean truncated) {
        for (LogRecord r : records) {
            if (!(r.log() instanceof WithItemStackLog wi) || !(r.log() instanceof WithLPriceLog wp)) continue;
            Material material = items.get(wi.item());
            if (material == null || wi.count() <= 0) continue;
            reference.computeIfAbsent(material, m -> new ArrayList<>()).add(wp.lprice() / wi.count());
        }
        Map<Material, Long> medians = new HashMap<>();
        for (Map.Entry<Material, List<Long>> e : reference.entrySet()) {
            List<Long> prices = e.getValue();
            if (prices.size() < 3) continue;
            prices.sort(Long::compare);
            medians.put(e.getKey(), prices.get(prices.size() / 2));
        }

        Map<UUID, Seller> sellers = new HashMap<>();
        for (LogRecord r : records) {
            if (!(r.log() instanceof WithItemStackLog wi) || !(r.log() instanceof WithLPriceLog wp)) continue;
            UUID seller = r.subject();
            if (seller == null || wi.count() <= 0) continue;
            Material material = items.get(wi.item());
            Long median = material == null ? null : medians.get(material);
            if (median == null || median <= 0) continue;
            long perOne = wp.lprice() / wi.count();
            long ratio = perOne * 100 / median;
            if (ratio < threshold) continue;
            Seller st = sellers.computeIfAbsent(seller, k -> new Seller());
            st.sales++;
            st.totalCents += wp.lprice();
            st.maxRatio = Math.max(st.maxRatio, ratio);
            if (r.actor() != null) st.buyers.add(r.actor());
        }

        List<Map.Entry<UUID, Seller>> sorted = new ArrayList<>(sellers.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue().totalCents, a.getValue().totalCents));
        List<Map.Entry<UUID, Seller>> top = sorted.subList(0, Math.min(limit, sorted.size()));

        if (top.isEmpty()) {
            sender.sendMessage(Lang.get("overpriced.empty", "hours", hours, "threshold", threshold));
            return;
        }
        sender.sendMessage(Messages.header(Lang.rawFormatted("overpriced.header",
                "count", top.size(), "hours", hours, "threshold", threshold)));
        printSellers(sender, auction, top, 0, new HashMap<>());
        if (truncated) {
            sender.sendMessage(Lang.get("overpriced.truncated", "limit", records.size()));
        }
    }

    private void printSellers(CommandSender sender, Auction auction, List<Map.Entry<UUID, Seller>> rows, int index,
                              Map<UUID, String> cache) {
        if (index >= rows.size()) return;
        Map.Entry<UUID, Seller> row = rows.get(index);
        Seller st = row.getValue();
        NameResolver.resolve(auction, row.getKey(), cache, name -> {
            sender.sendMessage(Lang.get("overpriced.line",
                    "seller", name != null ? name : row.getKey().toString(),
                    "sales", st.sales,
                    "buyers", st.buyers.size(),
                    "volume", Formatters.moneyFromCents(st.totalCents),
                    "max", st.maxRatio));
            printSellers(sender, auction, rows, index + 1, cache);
        });
    }

    private static final class Seller {
        private int sales;
        private long totalCents;
        private long maxRatio;
        private final Set<UUID> buyers = new HashSet<>();
    }

    private void printPairs(CommandSender sender, Auction auction, List<Pair> pairs, int index, Map<UUID, String> cache) {
        if (index >= pairs.size()) return;
        Pair pair = pairs.get(index);
        NameResolver.resolve(auction, pair.a, cache, nameA ->
                NameResolver.resolve(auction, pair.b, cache, nameB -> {
                    sender.sendMessage(Lang.get("laundering.line",
                            "a", nameA != null ? nameA : pair.a.toString(),
                            "b", nameB != null ? nameB : pair.b.toString(),
                            "a_to_b", pair.aToB, "b_to_a", pair.bToA,
                            "volume", Formatters.moneyFromCents(pair.totalCents)));
                    printPairs(sender, auction, pairs, index + 1, cache);
                }));
    }

    private static final class Pair {
        private final UUID a;
        private final UUID b;
        private int aToB;
        private int bToA;
        private long totalCents;

        private Pair(UUID a, UUID b) {
            this.a = a;
            this.b = b;
        }
    }
}
