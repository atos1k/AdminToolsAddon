package dev.atos1k.auc;

import dev.atos1k.auc.command.AdminCommands;
import dev.atos1k.auc.command.MenuCommands;
import dev.atos1k.auc.command.Permissions;
import dev.atos1k.auc.handler.WatchHandler;
import dev.atos1k.auc.manager.BanManager;
import dev.atos1k.auc.manager.BlacklistManager;
import dev.atos1k.auc.manager.StorageProvider;
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
import java.lang.reflect.Field;
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
    private final Map<Class<?>, Field> whoFields = new ConcurrentHashMap<>();
    private final Map<UUID, WatchFilter> watchers = new ConcurrentHashMap<>();
    private volatile WatchFilter consoleWatch = null;
    private volatile Auction listenerRegisteredOn = null;

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
            if (!hasWatchers()) return;
            watchHandler().broadcastLiveEvent(auction, record);
        });
        listenerRegisteredOn = auction;
    }

    @Override
    public <T> boolean doSkipTransaction(Transaction<T> transaction) {
        BanTypes type;
        UUID who;
        if (transaction instanceof BuyLotTransaction t) {
            type = BanTypes.BUY;
            who = readWho(t);
        } else if (transaction instanceof AddLotTransaction t) {
            String match = blacklist == null ? null : blacklist.findMatch(t.itemStack());
            if (match != null) {
                Player player = Bukkit.getPlayer(t.who());
                if (player != null) {
                    player.sendMessage(Lang.get("blacklist.denied", "value", match));
                }
                return true;
            }
            type = BanTypes.SELL;
            who = t.who();
        } else if (transaction instanceof TakeLotTransaction t) {
            type = BanTypes.TAKE;
            who = readWho(t);
        } else if (transaction instanceof TakeVaultLotTransaction t) {
            type = BanTypes.VAULT;
            who = readWho(t);
        } else if (transaction instanceof ResellTransaction t) {
            type = BanTypes.RESELL;
            who = readWho(t);
        } else {
            return false;
        }
        if (who == null || bans == null || bans.isEmpty()) return false;
        BanEntry entry = bans.active(who, type);
        if (entry == null) return false;
        Player player = Bukkit.getPlayer(who);
        if (player != null) {
            player.sendMessage(Lang.get("ban.denied." + type.id()));
            if (entry.reason() != null && !entry.reason().isBlank()) {
                player.sendMessage(Lang.get("ban.denied-reason", "reason", entry.reason()));
            }
            player.sendMessage(entry.permanent()
                    ? Lang.get("ban.denied-permanent")
                    : Lang.get("ban.denied-until", "time",
                    DurationUtil.format(entry.expires() - System.currentTimeMillis())));
        }
        return true;
    }

    private UUID readWho(Object transaction) {
        Class<?> cl = transaction.getClass();
        Field field = whoFields.get(cl);
        if (field == null) {
            try {
                field = cl.getDeclaredField("who");
                field.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                return null;
            }
            whoFields.put(cl, field);
        }
        try {
            return (UUID) field.get(transaction);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Override
    public Command<CommandSender> bootUserCommands(Command<CommandSender> base) {
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
        player.sendMessage(Lang.get("ban.denied.all"));
        if (entry == null) return;
        if (entry.reason() != null && !entry.reason().isBlank()) {
            player.sendMessage(Lang.get("ban.denied-reason", "reason", entry.reason()));
        }
        player.sendMessage(entry.permanent()
                ? Lang.get("ban.denied-permanent")
                : Lang.get("ban.denied-until", "time",
                DurationUtil.format(entry.expires() - System.currentTimeMillis())));
    }

    @Override
    public Command<CommandSender> bootAdminCommands(Command<CommandSender> base) {
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
        Lang.load(folder);
        storage = StorageProvider.open(folder, msg -> getLogger().warn(msg));
        bans = new BanManager(storage);
        bans.load(msg -> getLogger().warn(msg));
        blacklist = new BlacklistManager(storage);
        blacklist.load(msg -> getLogger().warn(msg));
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
        var base = pm.getPermission(Permissions.BASE);
        if (base != null) {
            pm.removePermission(base);
        }
    }

    private void registerPermissions() {
        var pm = Bukkit.getPluginManager();
        Map<String, Boolean> child = new LinkedHashMap<>();
        for (String node : Permissions.CHILD) {
            if (pm.getPermission(node) == null) {
                pm.addPermission(new Permission(node, "Команда /aha " + node.substring(Permissions.BASE.length() + 1),
                        PermissionDefault.OP));
            }
            child.put(node, true);
        }
        if (pm.getPermission(Permissions.BASE) == null) {
            pm.addPermission(new Permission(Permissions.BASE, "Доступ ко всем админ-функциям аукциона",
                    PermissionDefault.OP, child));
        }
    }
}