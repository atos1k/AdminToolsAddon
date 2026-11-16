package dev.atos1k.auc.handler;

import dev.atos1k.auc.util.AddonConfig;
import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.manager.MarketLimits;
import dev.atos1k.auc.manager.PriceRules;
import dev.atos1k.auc.util.Formatters;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.LogTypes;
import dev.atos1k.auc.util.Messages;
import dev.by1337.auc.common.auc.log.LogQuery;
import dev.by1337.auc.common.auc.log.LogRecord;
import dev.by1337.auc.common.auc.log.impl.WithItemStackLog;
import dev.by1337.auc.common.auc.log.impl.WithLPriceLog;
import dev.by1337.auc.handler.Auction;
import dev.by1337.auc.util.number.EconomyUtil;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

public final class PriceToolsHandler {
    private static final long DAY_MILLIS = 86_400_000L;

    private final AdminToolsAddon addon;

    public PriceToolsHandler(AdminToolsAddon addon) {
        this.addon = addon;
    }

    private boolean denied(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return false;
        Lang.send(sender, "general.no-permission");
        return true;
    }

    public void handlePriceRuleSet(CommandSender sender, Material material, Double min, Double max) {
        if (denied(sender, Permissions.PRICERULE)) return;
        if (material == null || min == null || min < 0 || (max != null && max < 0)) {
            Lang.send(sender, "usage.pricerule");
            return;
        }
        double upper = max == null ? 0.0 : max;
        if (upper > 0 && upper < min) {
            Lang.send(sender, "pricerule.bad-range");
            return;
        }
        addon.priceRules().set(material.name(), EconomyUtil.toCents(min), EconomyUtil.toCents(upper));
        Lang.send(sender, "pricerule.set", "material", material.name(),
                "min", min > 0 ? Formatters.money(min) : "—",
                "max", upper > 0 ? Formatters.money(upper) : "—");
    }

    public void handlePriceRuleRemove(CommandSender sender, Material material) {
        if (denied(sender, Permissions.PRICERULE)) return;
        if (material == null) {
            Lang.send(sender, "usage.pricerule");
            return;
        }
        sender.sendMessage(addon.priceRules().remove(material.name())
                ? Lang.get("pricerule.removed", "material", material.name())
                : Lang.get("pricerule.not-found", "material", material.name()));
    }

    public void handlePriceRuleList(CommandSender sender) {
        if (denied(sender, Permissions.PRICERULE)) return;
        Map<String, PriceRules.Rule> all = addon.priceRules().all();
        if (all.isEmpty()) {
            Lang.send(sender, "pricerule.list-empty");
            return;
        }
        sender.sendMessage(Messages.header(Lang.raw("pricerule.list-header")));
        for (Map.Entry<String, PriceRules.Rule> entry : all.entrySet()) {
            PriceRules.Rule rule = entry.getValue();
            Lang.send(sender, "pricerule.list-line", "material", entry.getKey(),
                    "min", rule.minCents() > 0 ? Formatters.moneyFromCents(rule.minCents()) : "—",
                    "max", rule.maxCents() > 0 ? Formatters.moneyFromCents(rule.maxCents()) : "—");
        }
    }

    public void handleRateLimit(CommandSender sender, String what, Integer value) {
        if (denied(sender, Permissions.RATELIMIT)) return;
        MarketLimits limits = addon.limits();
        if (what == null || what.equalsIgnoreCase("show")) {
            sender.sendMessage(Messages.header(Lang.raw("ratelimit.header")));
            Lang.send(sender, "ratelimit.line", "name", Lang.raw("ratelimit.name-sell"),
                    "value", describeLimit(limits.limit(MarketLimits.Kind.SELL)));
            Lang.send(sender, "ratelimit.line", "name", Lang.raw("ratelimit.name-buy"),
                    "value", describeLimit(limits.limit(MarketLimits.Kind.BUY)));
            Lang.send(sender, "ratelimit.line", "name", Lang.raw("ratelimit.name-window"), "value", limits.windowMinutes() + Lang.raw("ratelimit.minutes"));
            return;
        }
        if (value == null || value < 0) {
            Lang.send(sender, "usage.ratelimit");
            return;
        }
        switch (what.toLowerCase(Locale.ROOT)) {
            case "sell" -> limits.setLimit(MarketLimits.Kind.SELL, value);
            case "buy" -> limits.setLimit(MarketLimits.Kind.BUY, value);
            case "window" -> limits.setWindowMinutes(Math.max(1, value));
            default -> {
                Lang.send(sender, "usage.ratelimit");
                return;
            }
        }
        Lang.send(sender, "ratelimit.changed");
    }

    private static String describeLimit(int limit) {
        return limit <= 0 ? Lang.raw("ratelimit.unlimited") : limit + Lang.raw("ratelimit.per-window");
    }

    public void handleDumpAlert(CommandSender sender, Integer percent) {
        if (denied(sender, Permissions.DUMPALERT)) return;
        MarketLimits limits = addon.limits();
        if (percent == null) {
            sender.sendMessage(limits.dumpPercent() <= 0
                    ? Lang.get("dumpalert.off")
                    : Lang.get("dumpalert.status", "percent", limits.dumpPercent()));
            return;
        }
        limits.setDumpPercent(percent);
        sender.sendMessage(limits.dumpPercent() <= 0
                ? Lang.get("dumpalert.off")
                : Lang.get("dumpalert.set", "percent", limits.dumpPercent()));
    }

    public void handleChart(CommandSender sender, Auction auction, Material material, Integer daysArg) {
        if (denied(sender, Permissions.CHART)) return;
        if (material == null) {
            Lang.send(sender, "usage.chart");
            return;
        }
        int days = Math.max(2, AddonConfig.days("chart", daysArg, 7, 30));
        long after = System.currentTimeMillis() - days * DAY_MILLIS;
        LogQuery query = new LogQuery(null, null, after, null, null, null, LogTypes.TYPE_ALIASES.get("buy"), AddonConfig.scanLimit("chart", 5000));
        Lang.send(sender, "chart.counting", "material", material.name());
        auction.loadLogs(query).then(records -> {
            List<long[]> sales = new ArrayList<>();
            Set<Integer> ids = new LinkedHashSet<>();
            if (records != null) {
                for (LogRecord record : records) {
                    if (record.log() instanceof WithItemStackLog withItem && record.log() instanceof WithLPriceLog withPrice) {
                        int count = Math.max(1, withItem.count());
                        sales.add(new long[]{withItem.item(), record.timestamp(), withPrice.lprice() / count, count});
                        ids.add(withItem.item());
                    }
                }
            }
            if (ids.isEmpty()) {
                Lang.send(sender, "chart.none", "material", material.name());
                return;
            }
            List<Integer> toLoad = new ArrayList<>(ids);
            int maxItems = Math.max(1, AddonConfig.i("chart.max-items", 400));
            if (toLoad.size() > maxItems) toLoad = toLoad.subList(0, maxItems);
            Map<Integer, Material> materials = new ConcurrentHashMap<>();
            AtomicInteger left = new AtomicInteger(toLoad.size());
            for (int id : toLoad) {
                auction.loadItem(id).then(item -> {
                    if (item != null) materials.put(id, item.material());
                    if (left.decrementAndGet() == 0) printChart(sender, material, days, after, sales, materials);
                });
            }
        });
    }

    private void printChart(CommandSender sender, Material material, int days, long after, List<long[]> sales,
                            Map<Integer, Material> materials) {
        double[] sum = new double[days];
        long[] units = new long[days];
        long[] deals = new long[days];
        for (long[] sale : sales) {
            if (materials.get((int) sale[0]) != material) continue;
            int day = (int) Math.max(0, Math.min(days - 1, (sale[1] - after) / DAY_MILLIS));
            sum[day] += (double) sale[2] * sale[3];
            units[day] += sale[3];
            deals[day]++;
        }
        long totalDeals = 0;
        double min = Double.MAX_VALUE;
        double max = 0;
        double all = 0;
        long allUnits = 0;
        double[] avg = new double[days];
        for (int i = 0; i < days; i++) {
            totalDeals += deals[i];
            avg[i] = units[i] > 0 ? sum[i] / units[i] : -1;
            if (avg[i] >= 0) {
                min = Math.min(min, avg[i]);
                max = Math.max(max, avg[i]);
                all += sum[i];
                allUnits += units[i];
            }
        }
        if (totalDeals == 0) {
            Lang.send(sender, "chart.none", "material", material.name());
            return;
        }
        String sparkChars = AddonConfig.s("chart.spark-chars", "▁▂▃▄▅▆▇█");
        if (sparkChars.length() < 2) sparkChars = "▁▂▃▄▅▆▇█";
        StringBuilder spark = new StringBuilder();
        for (int i = 0; i < days; i++) {
            if (avg[i] < 0) {
                spark.append('·');
                continue;
            }
            int level = max > min ? (int) Math.round((avg[i] - min) / (max - min) * (sparkChars.length() - 1)) : sparkChars.length() / 2;
            spark.append(sparkChars.charAt(level));
        }
        sender.sendMessage(Messages.header(Lang.rawFormatted("chart.header", "material", material.name(), "days", days)));
        Lang.send(sender, "chart.spark", "spark", spark.toString());
        SimpleDateFormat format = new SimpleDateFormat("dd.MM");
        double previous = -1;
        for (int i = 0; i < days; i++) {
            String label = format.format(new Date(after + i * DAY_MILLIS));
            if (avg[i] < 0) {
                Lang.send(sender, "chart.line-empty", "day", label);
                continue;
            }
            String arrow = previous < 0 ? Lang.raw("chart.arrow-flat") : avg[i] > previous ? Lang.raw("chart.arrow-up") : avg[i] < previous ? Lang.raw("chart.arrow-down") : Lang.raw("chart.arrow-flat");
            previous = avg[i];
            Lang.send(sender, "chart.line", "day", label, "avg", Formatters.moneyFromCents((long) avg[i]),
                    "arrow", arrow, "deals", deals[i]);
        }
        Lang.send(sender, "chart.summary", "min", Formatters.moneyFromCents((long) min),
                "avg", Formatters.moneyFromCents((long) (all / Math.max(1, allUnits))),
                "max", Formatters.moneyFromCents((long) max), "deals", totalDeals);
    }
}
