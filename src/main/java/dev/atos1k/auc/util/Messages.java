package dev.atos1k.auc.util;

import dev.by1337.auc.common.auc.log.AuctionLog;
import dev.by1337.auc.common.auc.log.LogRecord;
import net.kyori.adventure.text.Component;

public final class Messages {
    private Messages() {
    }

    public static Component header(String text) {
        return Lang.get("general.header-format", "text", text);
    }

    public static Component kv(String key, String value) {
        return Lang.get("general.kv-format", "key", key, "value", value);
    }

    public static Component info(String text) {
        return Lang.get("general.info-format", "text", text);
    }

    public static Component err(String text) {
        return Lang.get("general.error-format", "text", text);
    }

    public static Component deny() {
        return Lang.get("general.no-permission");
    }
    
    public static Component txLine(LogRecord record, AuctionLog log, String actorName, String subjectName,
                                   String itemName, Integer itemId, int count, String price) {
        String subject = subjectName != null
                ? Lang.rawFormatted("transactions.line-subject", "subject", subjectName)
                : "";
        String item = "";
        if (!itemName.equals("-")) {
            String id = itemId != null ? Lang.rawFormatted("transactions.line-item-id", "id", itemId) : "";
            item = Lang.rawFormatted("transactions.line-item", "item", itemName, "count", count, "item_id", id);
        }
        String pricePart = !price.equals("-")
                ? Lang.rawFormatted("transactions.line-price", "price", price)
                : "";
        return Lang.get("transactions.line",
                "uid", record.uid(),
                "time", Formatters.date(record.timestamp()),
                "type", LogTypes.describe(log.type()),
                "actor", actorName != null ? actorName : "-",
                "subject", subject,
                "item", item,
                "price", pricePart);
    }
}
