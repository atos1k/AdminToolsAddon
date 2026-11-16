package dev.atos1k.auc;

import dev.atos1k.auc.util.AddonConfig;
import dev.atos1k.auc.command.AdminCommands;
import dev.atos1k.auc.command.MenuCommands;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.handler.AlertHandler;
import dev.atos1k.auc.handler.LiveMarketHandler;
import dev.atos1k.auc.handler.WatchHandler;
import dev.atos1k.auc.manager.BanManager;
import dev.atos1k.auc.manager.BlacklistManager;
import dev.atos1k.auc.manager.MarketLimits;
import dev.atos1k.auc.manager.PriceAlerts;
import dev.atos1k.auc.manager.PriceRules;
import dev.atos1k.auc.manager.StorageProvider;
import dev.atos1k.auc.util.Formatters;
import dev.atos1k.auc.storage.BanEntry;
import dev.atos1k.auc.storage.Storage;
import dev.atos1k.auc.util.BanTypes;
import dev.atos1k.auc.util.DurationUtil;
import dev.atos1k.auc.util.Lang;
import dev.atos1k.auc.util.WatchFilter;
import dev.by1337.auc.BAuction;
import dev.by1337.auc.addon.AbstractAddon;
import dev.by1337.auc.addon.BAucAddon;
import dev.by1337.auc.handler.Auction;
import dev.by1337.auc.transaction.AddLotTransaction;
import dev.by1337.auc.transaction.BuyLotTransaction;
import dev.by1337.auc.transaction.ResellTransaction;
import dev.by1337.auc.transaction.TakeLotTransaction;
import dev.by1337.auc.transaction.TakeVaultLotTransaction;
import dev.by1337.auc.transaction.Transaction;
import dev.by1337.bmenu.command.ExecuteContext;
import dev.by1337.cmd.Command;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

@BAucAddon(name = "AdminTools")
public class AdminToolsAddon extends AbstractAddon {
    private WatchHandler watchHandler;
    private Storage storage;
    private BanManager bans;
    private BlacklistManager blacklist;
    private PriceRules priceRules;
    private MarketLimits limits;
    private PriceAlerts alerts;
    private LiveMarketHandler liveMarket;
    private final Map<UUID, WatchFilter> watchers = new ConcurrentHashMap<>();
    private volatile WatchFilter consoleWatch = null;
    private volatile Auction listenerRegisteredOn = null;
    private volatile Command<CommandSender> adminRoot;
    private volatile Command<CommandSender> wrappedReload;

    public File dataFolder() {
        File folder = new File(new File(getPlugin().getDataFolder(), "addons"), getDescription().name());
        if (!folder.exists()) folder.mkdirs();
        return folder;
    }

    public WatchHandler watchHandler() {
        if (watchHandler == null) watchHandler = new WatchHandler(this);
        return watchHandler;
    }

    public BanManager bans() {
        return bans;
    }

    public BlacklistManager blacklist() {
        return blacklist;
    }

    public PriceRules priceRules() {
        return priceRules;
    }

    public MarketLimits limits() {
        return limits;
    }

    public PriceAlerts alerts() {
        return alerts;
    }

    public boolean toggleWatcher(UUID uuid) {
        ensureListenerRegistered();
        if (watchers.remove(uuid) != null) return false;
        watchers.put(uuid, WatchFilter.NONE);
        return true;
    }

    public boolean toggleConsoleWatch() {
        ensureListenerRegistered();
        if (consoleWatch != null) {
            consoleWatch = null;
            return false;
        }
        consoleWatch = WatchFilter.NONE;
        return true;
    }

    public WatchFilter watchFilter(UUID uuid) {
        return uuid == null ? consoleWatch : watchers.get(uuid);
    }

    public void setWatchFilter(UUID uuid, WatchFilter filter) {
        ensureListenerRegistered();
        if (uuid == null) {
            consoleWatch = filter;
        } else {
            watchers.put(uuid, filter);
        }
    }

    public void dispatchToWatchers(Component component, UUID actor, UUID subject, String logType, long priceCents) {
        WatchFilter console = consoleWatch;
        if (console != null && console.matches(actor, subject, logType, priceCents)) {
            Bukkit.getConsoleSender().sendMessage(component);
        }
        for (Map.Entry<UUID, WatchFilter> entry : watchers.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) continue;
            if (entry.getValue().matches(actor, subject, logType, priceCents)) {
                p.sendMessage(component);
            }
        }
    }

    public boolean hasWatchers() {
        return consoleWatch != null || !watchers.isEmpty();
    }

    private void ensureListenerRegistered() {
        Auction auction = BAuction.auction();
        if (auction == null || auction == listenerRegisteredOn) return;
        auction.registerLogListener(record -> {
            if (hasWatchers()) watchHandler().broadcastLiveEvent(auction, record);
            if (liveMarket != null) liveMarket.onRecord(auction, record);
        });
        listenerRegisteredOn = auction;
    }

    @Override
    public <T> boolean doSkipTransaction(Transaction<T> transaction) {
        BanTypes type;
        UUID who;
        if (transaction instanceof BuyLotTransaction t) {
            type = BanTypes.BUY;
            who = t.who();
        } else if (transaction instanceof AddLotTransaction t) {
            String match = blacklist == null ? null : blacklist.findMatch(t.itemStack());
            if (match != null) {
                Player player = Bukkit.getPlayer(t.who());
                if (player != null) {
                    Lang.send(player, "blacklist.denied", "value", match);
                }
                return true;
            }
            type = BanTypes.SELL;
            who = t.who();
        } else if (transaction instanceof TakeLotTransaction t) {
            type = BanTypes.TAKE;
            who = t.who();
        } else if (transaction instanceof TakeVaultLotTransaction t) {
            type = BanTypes.VAULT;
            who = t.who();
        } else if (transaction instanceof ResellTransaction t) {
            type = BanTypes.RESELL;
            who = t.who();
        } else {
            return false;
        }
        if (who == null) return false;
        if (denyByBan(type, who)) return true;
        if (isStaff(who)) return false;
        if (transaction instanceof AddLotTransaction add && denyByPriceRule(add)) return true;
        if (type == BanTypes.SELL) return denyByRate(who, MarketLimits.Kind.SELL);
        if (type == BanTypes.BUY) return denyByRate(who, MarketLimits.Kind.BUY);
        return false;
    }

    private boolean isStaff(UUID who) {
        if (!AddonConfig.b("bypass.enabled", true)) return false;
        Player player = Bukkit.getPlayer(who);
        return player != null && player.hasPermission(Permissions.BYPASS);
    }

    private boolean denyByPriceRule(AddLotTransaction transaction) {
        if (priceRules == null || priceRules.isEmpty() || !AddonConfig.b("pricerule.enabled", true)) return false;
        PriceRules.Violation violation = priceRules.check(transaction.itemStack(), transaction.centsPrice(), transaction.count());
        if (violation == null) return false;
        Player player = Bukkit.getPlayer(transaction.who());
        if (player != null) {
            Lang.send(player, violation.tooLow() ? "pricerule.denied-min" : "pricerule.denied-max",
                    "material", violation.material(), "limit", Formatters.moneyFromCents(violation.limitCents()));
        }
        return true;
    }

    private boolean denyByRate(UUID who, MarketLimits.Kind kind) {
        if (limits == null || !AddonConfig.b("ratelimit.enabled", true)) return false;
        long wait = limits.acquire(who, kind);
        if (wait <= 0L) return false;
        Player player = Bukkit.getPlayer(who);
        if (player != null) {
            Lang.send(player, kind == MarketLimits.Kind.SELL ? "ratelimit.denied-sell" : "ratelimit.denied-buy",
                    "seconds", wait);
        }
        return true;
    }

    private boolean denyByBan(BanTypes type, UUID who) {
        if (bans == null || bans.isEmpty()) return false;
        BanEntry entry = bans.active(who, type);
        if (entry == null) return false;
        Player player = Bukkit.getPlayer(who);
        if (player != null) {
            Lang.send(player, "ban.denied." + type.id());
            if (entry.reason() != null && !entry.reason().isBlank()) {
                Lang.send(player, "ban.denied-reason", "reason", entry.reason());
            }
            if (entry.permanent()) {
                Lang.send(player, "ban.denied-permanent");
            } else {
                Lang.send(player, "ban.denied-until", "time",
                        DurationUtil.format(entry.expires() - System.currentTimeMillis()));
            }
        }
        return true;
    }

    @Override
    public Command<CommandSender> bootUserCommands(Command<CommandSender> base) {
        try {
            AlertHandler.install(this, base);
        } catch (Throwable e) {
            getLogger().error("Не удалось добавить команду /{} alert", base.name(), e);
        }
        guardUserCommand(base, Collections.newSetFromMap(new IdentityHashMap<>()));
        return base;
    }

    private void guardUserCommand(Command<CommandSender> node, Set<Command<CommandSender>> visited) {
        if (!visited.add(node)) return;
        var original = node.getExecutor();
        node.executor((sender, args) -> {
            if (!allowUserCommand(sender)) {
                sendBanNotice(sender);
                return;
            }
            if (original != null) original.execute(sender, args);
        });
        node.getSubCommands().values().forEach(sub -> guardUserCommand(sub, visited));
    }

    private void sendBanNotice(CommandSender sender) {
        if (!(sender instanceof Player player) || bans == null) return;
        BanEntry entry = bans.active(player.getUniqueId(), BanTypes.ALL);
        Lang.send(player, "ban.denied.all");
        if (entry == null) return;
        if (entry.reason() != null && !entry.reason().isBlank()) {
            Lang.send(player, "ban.denied-reason", "reason", entry.reason());
        }
        if (entry.permanent()) {
            Lang.send(player, "ban.denied-permanent");
        } else {
            Lang.send(player, "ban.denied-until", "time",
                    DurationUtil.format(entry.expires() - System.currentTimeMillis()));
        }
    }

    @Override
    public void onPostEnabled(BAuction auction) {
        wrapReload();
    }

    private void wrapReload() {
        Command<CommandSender> root = adminRoot;
        if (root == null) return;
        Command<CommandSender> reload = root.getSubCommands().get("reload");
        if (reload == null || reload == wrappedReload) return;
        var original = reload.getExecutor();
        if (original == null) return;
        reload.executor((sender, args) -> {
            long nanos = System.nanoTime();
            original.execute(muted(sender), args);
            Lang.reload();
            Lang.send(sender, "reload.success", "time",
                    String.format("%.1f", (System.nanoTime() - nanos) / 1_000_000D));
        });
        wrappedReload = reload;
    }

    private static CommandSender muted(CommandSender sender) {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage")) return null;
                    return method.invoke(sender, args);
                });
    }

    @Override
    public Command<CommandSender> bootAdminCommands(Command<CommandSender> base) {
        adminRoot = base;
        try {
            return AdminCommands.install(this, base);
        } catch (Throwable e) {
            getLogger().error("Не удалось добавить админ-команды в /{}", base.name(), e);
            return base;
        }
    }

    @Override
    public Command<ExecuteContext> bootMenuCommand(Command<ExecuteContext> base) {
        return MenuCommands.install(this, base);
    }

    private boolean allowUserCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;
        return bans == null || !bans.isBannedCompletely(player.getUniqueId());
    }

    @Override
    protected void onEnable() {
        File folder = dataFolder();
        Lang.load(folder, getPlugin());
        AddonConfig.load(folder);
        storage = StorageProvider.open(folder, msg -> getLogger().warn(msg));
        bans = new BanManager(storage);
        bans.load(msg -> getLogger().warn(msg));
        blacklist = new BlacklistManager(storage);
        blacklist.load(msg -> getLogger().warn(msg));
        priceRules = new PriceRules(folder, msg -> getLogger().warn(msg));
        priceRules.load();
        limits = new MarketLimits(folder, msg -> getLogger().warn(msg));
        limits.load();
        alerts = new PriceAlerts(folder, msg -> getLogger().warn(msg));
        alerts.load();
        liveMarket = new LiveMarketHandler(this);
        registerPermissions();
        ensureListenerRegistered();
        getLogger().info("AdminToolsAddon включен. Команды доступны в /aha");
    }

    @Override
    protected void onDisable() {
        if (storage != null) {
            storage.close();
            storage = null;
        }
        watchers.clear();
        consoleWatch = null;
        var pm = Bukkit.getPluginManager();
        for (String node : Permissions.CHILD) {
            var perm = pm.getPermission(node);
            if (perm != null) {
                pm.removePermission(perm);
            }
        }
        var base = pm.getPermission(Permissions.AHA);
        if (base != null) {
            pm.removePermission(base);
        }
        var alertPerm = pm.getPermission(Permissions.ALERT);
        if (alertPerm != null) {
            pm.removePermission(alertPerm);
        }
    }

    private void registerPermissions() {
        var pm = Bukkit.getPluginManager();
        if (pm.getPermission(Permissions.ALERT) == null) {
            pm.addPermission(new Permission(Permissions.ALERT, "Оповещения о ценах /ah alert", PermissionDefault.OP));
        }
        Map<String, Boolean> child = new LinkedHashMap<>();
        for (String node : Permissions.CHILD) {
            if (pm.getPermission(node) == null) {
                pm.addPermission(new Permission(node, "Команда /aha " + node.substring(Permissions.AHA.length() + 1),
                        PermissionDefault.OP));
            }
            child.put(node, true);
        }
        if (pm.getPermission(Permissions.AHA) == null) {
            pm.addPermission(new Permission(Permissions.AHA, "Доступ ко всем админ-функциям аукциона",
                    PermissionDefault.OP, child));
        }
    }
}