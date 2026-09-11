package dev.atos1k.auc.util;

import dev.by1337.auc.common.auc.log.AuctionLog;
import dev.by1337.auc.common.auc.log.LogRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class Messages {

    private Messages() {
    }

    public static Component header(String text) {
        return Component.text("▬▬ " + text + " ▬▬", NamedTextColor.GOLD);
    }

    public static Component kv(String key, String value) {
        return Component.text(key + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    public static Component info(String text) {
        return Component.text(text, NamedTextColor.AQUA);
    }

    public static Component err(String text) {
        return Component.text(text, NamedTextColor.RED);
    }

    public static Component deny() {
        return Component.text("У вас нет прав на использование этой команды.", NamedTextColor.RED);
    }
    
    public static Component txLine(LogRecord record, AuctionLog log, String actorName, String subjectName, String itemName, Integer itemId, int count, String price) {
        String time = Formatters.date(record.timestamp());
        Component c = Component.text("#" + record.uid() + " ", NamedTextColor.DARK_GRAY)
                .append(Component.text(time + " ", NamedTextColor.GRAY))
                .append(Component.text("[" + LogTypes.describe(log.type()) + "] ", NamedTextColor.AQUA))
                .append(Component.text(actorName != null ? actorName : "-", NamedTextColor.GREEN));
        if (subjectName != null) {
            c = c.append(Component.text(" → ", NamedTextColor.GRAY)).append(Component.text(subjectName, NamedTextColor.YELLOW));
        }
        if (!itemName.equals("-")) {
            c = c.append(Component.text("  " + itemName + " x" + count, NamedTextColor.WHITE));
            if (itemId != null) {
                c = c.append(Component.text(" (item#" + itemId + ")", NamedTextColor.DARK_GRAY));
            }
        }
        if (!price.equals("-")) {
            c = c.append(Component.text("  " + price, NamedTextColor.GOLD));
        }
        return c;
    }
}
