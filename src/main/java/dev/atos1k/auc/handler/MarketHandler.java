package dev.atos1k.auc.handler;

import dev.atos1k.auc.AdminToolsAddon;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.util.CommandUtil;
import dev.atos1k.auc.util.Formatters;
import dev.atos1k.auc.util.LotScanner;
import dev.atos1k.auc.util.Messages;
import dev.atos1k.auc.util.NameResolver;
import dev.by1337.auc.auc.ClientAucLot;
import dev.by1337.auc.handler.Auction;
import dev.by1337.auc.handler.SimpleAuction;
import dev.by1337.auc.util.number.EconomyUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MarketHandler {

    private final AdminToolsAddon addon;

    public MarketHandler(AdminToolsAddon addon) {
        this.addon = addon;
    }
    
    public void handleLiquid(CommandSender sender, Auction auction, String[] args) {
        if (!sender.hasPermission(Permissions.LIQUID)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        int limit = 15;
        if (args.length > 0 && CommandUtil.isNumeric(args[0])) {
            limit = CommandUtil.clamp(Integer.parseInt(args[0]), 1, 50);
        }
        int finalLimit = limit;
        sender.sendMessage(Messages.info("Считаю ликвидность по активным лотам..."));
        SimpleAuction.WORKER.execute(() -> {
            List<ClientAucLot> lots = LotScanner.collectAllActive(auction);
            Map<Material, MaterialStats> stats = new HashMap<>();
            for (ClientAucLot lot : lots) {
                Material material = lot.itemStack().material();
                MaterialStats st = stats.computeIfAbsent(material, m -> new MaterialStats());
                st.lots++;
                st.items += lot.count();
                st.totalValueCents += lot.lprice();
            }
            int finalTotal = lots.size();
            List<Map.Entry<Material, MaterialStats>> sorted = new ArrayList<>(stats.entrySet());
            sorted.sort((a, b) -> Integer.compare(b.getValue().lots, a.getValue().lots));
            List<Map.Entry<Material, MaterialStats>> top = sorted.subList(0, Math.min(finalLimit, sorted.size()));

            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                sender.sendMessage(Messages.header("Топ ликвидных товаров (по кол-ву активных лотов)"));
                int rank = 1;
                for (var e : top) {
                    MaterialStats s = e.getValue();
                    double avg = s.lots == 0 ? 0 : EconomyUtil.fromCents(s.totalValueCents) / s.lots;
                    sender.sendMessage(Component.text(rank + ". ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(e.getKey().name(), NamedTextColor.WHITE))
                            .append(Component.text("  лотов: " + s.lots, NamedTextColor.AQUA))
                            .append(Component.text("  предметов: " + s.items, NamedTextColor.GREEN))
                            .append(Component.text("  сумма: " + Formatters.moneyFromCents(s.totalValueCents), NamedTextColor.GOLD))
                            .append(Component.text("  средняя цена лота: " + Formatters.money(avg), NamedTextColor.YELLOW)));
                    rank++;
                }
                sender.sendMessage(Messages.info("Всего активных лотов: " + finalTotal + ", уникальных предметов: " + stats.size()));
            });
        });
    }

    private static final class MaterialStats {
        int lots;
        long items;
        long totalValueCents;
    }


    public void handleStats(CommandSender sender, Auction auction) {
        if (!sender.hasPermission(Permissions.STATS)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        sender.sendMessage(Messages.info("Собираю статистику..."));
        SimpleAuction.WORKER.execute(() -> {
            List<ClientAucLot> lots = LotScanner.collectAllActive(auction);
            long totalValueCents = 0;
            long totalItems = 0;
            long maxPriceCents = 0;
            Set<Material> materials = new HashSet<>();
            for (ClientAucLot lot : lots) {
                totalValueCents += lot.lprice();
                totalItems += lot.count();
                materials.add(lot.itemStack().material());
                if (lot.lprice() > maxPriceCents) maxPriceCents = lot.lprice();
            }

            int finalLots = lots.size();
            long finalTotalValueCents = totalValueCents;
            long finalTotalItems = totalItems;
            long finalMaxPriceCents = maxPriceCents;
            int finalUnique = materials.size();

            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                sender.sendMessage(Messages.header("Статистика аукциона"));
                sender.sendMessage(Messages.kv("Активных лотов", String.valueOf(finalLots)));
                sender.sendMessage(Messages.kv("Предметов на аукционе", String.valueOf(finalTotalItems)));
                sender.sendMessage(Messages.kv("Общая стоимость активных лотов", Formatters.moneyFromCents(finalTotalValueCents)));
                sender.sendMessage(Messages.kv("Уникальных видов предметов", String.valueOf(finalUnique)));
                sender.sendMessage(Messages.kv("Самый дорогой активный лот", Formatters.moneyFromCents(finalMaxPriceCents)));
            });
        });
    }
    
    public void handlePrice(CommandSender sender, Auction auction, String[] args) {
        if (!sender.hasPermission(Permissions.PRICE)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(Messages.err("Использование: /baucadmin price <материал>"));
            return;
        }
        Material material = Material.matchMaterial(args[0]);
        if (material == null) {
            sender.sendMessage(Messages.err("Неизвестный материал: " + args[0]));
            return;
        }
        Material finalMaterial = material;
        SimpleAuction.WORKER.execute(() -> {
            List<ClientAucLot> lots = LotScanner.collectAllActive(auction);
            List<ClientAucLot> matching = new ArrayList<>();
            for (ClientAucLot lot : lots) {
                if (lot.itemStack().material() == finalMaterial) matching.add(lot);
            }
            matching.sort(Comparator.comparingLong(ClientAucLot::lprice_for_one));

            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                if (matching.isEmpty()) {
                    sender.sendMessage(Messages.info("Активных лотов с " + finalMaterial.name() + " сейчас нет."));
                    return;
                }
                long min = matching.get(0).lprice_for_one();
                long max = matching.get(matching.size() - 1).lprice_for_one();
                long sum = 0;
                for (ClientAucLot l : matching) sum += l.lprice_for_one();
                double avg = (double) sum / matching.size();

                sender.sendMessage(Messages.header("Рынок: " + finalMaterial.name()));
                sender.sendMessage(Messages.kv("Активных лотов", String.valueOf(matching.size())));
                sender.sendMessage(Messages.kv("Мин. цена за шт.", Formatters.moneyFromCents(min)));
                sender.sendMessage(Messages.kv("Средняя цена за шт.", Formatters.moneyFromCents((long) avg)));
                sender.sendMessage(Messages.kv("Макс. цена за шт.", Formatters.moneyFromCents(max)));
                sender.sendMessage(Component.text("Самые дешёвые лоты: ", NamedTextColor.GRAY));
                for (int i = 0; i < Math.min(5, matching.size()); i++) {
                    ClientAucLot l = matching.get(i);
                    sender.sendMessage(Component.text("  #" + l.uid() + "  ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(Formatters.moneyFromCents(l.lprice_for_one()) + "/шт.", NamedTextColor.GOLD))
                            .append(Component.text("  x" + l.count(), NamedTextColor.WHITE)));
                }
            });
        });
    }
    
    public void handleFind(CommandSender sender, Auction auction, String[] args) {
        if (!sender.hasPermission(Permissions.FIND)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(Messages.err("Использование: /baucadmin find <материал> [лимит]"));
            return;
        }
        Material material = Material.matchMaterial(args[0]);
        if (material == null) {
            sender.sendMessage(Messages.err("Неизвестный материал: " + args[0]));
            return;
        }
        int limit = 20;
        if (args.length > 1 && CommandUtil.isNumeric(args[1])) limit = CommandUtil.clamp(Integer.parseInt(args[1]), 1, 50);
        Material finalMaterial = material;
        int finalLimit = limit;

        SimpleAuction.WORKER.execute(() -> {
            List<ClientAucLot> lots = LotScanner.collectAllActive(auction);
            List<ClientAucLot> matching = new ArrayList<>();
            for (ClientAucLot lot : lots) {
                if (lot.itemStack().material() == finalMaterial) matching.add(lot);
                if (matching.size() >= finalLimit) break;
            }
            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                if (matching.isEmpty()) {
                    sender.sendMessage(Messages.info("Активных лотов с " + finalMaterial.name() + " не найдено."));
                    return;
                }
                sender.sendMessage(Messages.header("Найдено лотов: " + matching.size() + (matching.size() == finalLimit ? "+" : "")));
                printFindResults(sender, auction, matching, 0, new HashMap<>());
            });
        });
    }

    private void printFindResults(CommandSender sender, Auction auction, List<ClientAucLot> lots, int index, Map<UUID, String> cache) {
        if (index >= lots.size()) return;
        ClientAucLot lot = lots.get(index);
        NameResolver.resolve(auction, lot.owner(), cache, ownerName ->
                Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                    sender.sendMessage(Component.text("  #" + lot.uid() + "  ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(ownerName != null ? ownerName : lot.owner().toString(), NamedTextColor.GREEN))
                            .append(Component.text("  x" + lot.count(), NamedTextColor.WHITE))
                            .append(Component.text("  " + Formatters.money(lot.dprice()), NamedTextColor.GOLD)));
                    printFindResults(sender, auction, lots, index + 1, cache);
                }));
    }
    
    public void handleSuspicious(CommandSender sender, Auction auction, String[] args) {
        if (!sender.hasPermission(Permissions.SUSPICIOUS)) {
            sender.sendMessage(Messages.deny());
            return;
        }
        int thresholdPercent = 35;
        int limit = 20;
        for (String arg : args) {
            if (CommandUtil.isNumeric(arg)) {
                int v = Integer.parseInt(arg);
                if (v <= 100) thresholdPercent = CommandUtil.clamp(v, 1, 99);
                else limit = CommandUtil.clamp(v, 1, 50);
            }
        }
        int finalThreshold = thresholdPercent;
        int finalLimit = limit;
        sender.sendMessage(Messages.info("Ищу подозрительно дешёвые лоты (порог " + finalThreshold + "% от средней цены)..."));

        SimpleAuction.WORKER.execute(() -> {
            List<ClientAucLot> lots = LotScanner.collectAllActive(auction);
            Map<Material, long[]> agg = new HashMap<>(); 
            for (ClientAucLot lot : lots) {
                long[] entry = agg.computeIfAbsent(lot.itemStack().material(), m -> new long[2]);
                entry[0] += lot.lprice_for_one();
                entry[1] += 1;
            }
            List<Object[]> flagged = new ArrayList<>(); 
            for (ClientAucLot lot : lots) {
                long[] entry = agg.get(lot.itemStack().material());
                if (entry[1] < 3) continue;
                double avg = (double) entry[0] / entry[1];
                if (avg <= 0) continue;
                double ratio = lot.lprice_for_one() / avg;
                if (ratio * 100 <= finalThreshold) {
                    flagged.add(new Object[]{lot, avg, ratio});
                }
            }
            flagged.sort((a, b) -> Double.compare((double) a[2], (double) b[2]));
            List<Object[]> top = flagged.subList(0, Math.min(finalLimit, flagged.size()));

            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                if (top.isEmpty()) {
                    sender.sendMessage(Messages.info("Подозрительных лотов не найдено."));
                    return;
                }
                sender.sendMessage(Messages.header("Подозрительно дешёвые лоты (" + top.size() + ")"));
                printSuspicious(sender, auction, top, 0, new HashMap<>());
            });
        });
    }

    private void printSuspicious(CommandSender sender, Auction auction, List<Object[]> entries, int index, Map<UUID, String> cache) {
        if (index >= entries.size()) return;
        ClientAucLot lot = (ClientAucLot) entries.get(index)[0];
        double avg = (double) entries.get(index)[1];
        double ratio = (double) entries.get(index)[2];
        NameResolver.resolve(auction, lot.owner(), cache, ownerName -> {
            sender.sendMessage(Component.text("  #" + lot.uid() + "  ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(lot.itemStack().material().name(), NamedTextColor.WHITE))
                    .append(Component.text("  цена/шт: " + Formatters.moneyFromCents(lot.lprice_for_one()), NamedTextColor.RED))
                    .append(Component.text("  средняя: " + Formatters.moneyFromCents((long) avg), NamedTextColor.GRAY))
                    .append(Component.text("  (" + Math.round(ratio * 100) + "%)", NamedTextColor.YELLOW))
                    .append(Component.text("  владелец: " + (ownerName != null ? ownerName : lot.owner()), NamedTextColor.GREEN)));
            printSuspicious(sender, auction, entries, index + 1, cache);
        });
    }
}
