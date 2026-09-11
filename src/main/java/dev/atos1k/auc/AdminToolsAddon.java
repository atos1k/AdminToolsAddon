package dev.atos1k.auc;

import dev.by1337.auc.BAuction;
import dev.by1337.auc.addon.AbstractAddon;
import dev.by1337.auc.addon.BAucAddon;
import dev.atos1k.auc.command.AdminCommand;
import dev.atos1k.auc.command.Permissions;
import dev.by1337.auc.handler.Auction;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

@BAucAddon(name = "AdminTools")
public class AdminToolsAddon extends AbstractAddon {

    private AdminCommand adminCommand;


    private final Set<UUID> watchers = new CopyOnWriteArraySet<>();
    private volatile boolean consoleWatching = false;
    private volatile Auction listenerRegisteredOn = null;
    
    public boolean toggleWatcher(UUID uuid) {
        ensureListenerRegistered();
        if (!watchers.remove(uuid)) {
            watchers.add(uuid);
            return true;
        }
        return false;
    }

    public boolean toggleConsoleWatch() {
        ensureListenerRegistered();
        consoleWatching = !consoleWatching;
        return consoleWatching;
    }

    public void dispatchToWatchers(Component component) {
        if (consoleWatching) {
            Bukkit.getConsoleSender().sendMessage(component);
        }
        for (UUID uuid : watchers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage(component);
            }
        }
    }
    
    private void ensureListenerRegistered() {
        Auction auction = BAuction.auction();
        if (auction == null || auction == listenerRegisteredOn) return;
        auction.registerLogListener(record -> {
            if (!consoleWatching && watchers.isEmpty()) return;
            if (adminCommand != null) {
                adminCommand.watchHandler().broadcastLiveEvent(auction, record);
            }
        });
        listenerRegisteredOn = auction;
    }


    @Override
    protected void onEnable() {
        registerPermissions();
        adminCommand = new AdminCommand(this);
        registerCommand(adminCommand);
        ensureListenerRegistered();
        getLogger().info("AdminToolsAddon включен. Команда: /baucadmin (алиасы: /aucadm, /aucadmin)");
    }

    @Override
    protected void onDisable() {
        if (adminCommand != null) {
            unregisterCommand(adminCommand);
            adminCommand = null;
        }
        watchers.clear();
        consoleWatching = false;
        var pm = Bukkit.getPluginManager();
        for (String node : Permissions.ALL) {
            var perm = pm.getPermission(node);
            if (perm != null) {
                pm.removePermission(perm);
            }
        }
    }

    private void registerPermissions() {
        var pm = Bukkit.getPluginManager();
        for (String node : Permissions.ALL) {
            if (pm.getPermission(node) == null) {
                pm.addPermission(new Permission(node, PermissionDefault.OP));
            }
        }
    }
    
    private void registerCommand(Command command) {
        CommandMap commandMap = getCommandMap();
        if (commandMap != null) {
            commandMap.register(getPlugin().getName().toLowerCase(), command);
        }
    }

    private void unregisterCommand(Command command) {
        CommandMap commandMap = getCommandMap();
        if (commandMap instanceof SimpleCommandMap simpleCommandMap) {
            try {
                Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
                knownCommandsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                var knownCommands = (java.util.Map<String, Command>) knownCommandsField.get(simpleCommandMap);
                knownCommands.values().removeIf(c -> c == command);
            } catch (ReflectiveOperationException e) {
                getLogger().warn("Не удалось корректно снять команду с регистрации", e);
            }
        }
        command.unregister(commandMap);
    }

    private CommandMap getCommandMap() {
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            return (CommandMap) f.get(Bukkit.getServer());
        } catch (ReflectiveOperationException e) {
            getLogger().error("Не удалось получить CommandMap сервера", e);
            return null;
        }
    }
}
