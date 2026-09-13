package dev.atos1k.auc.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum BanTypes {
    ALL("all"),
    BUY("buy"),
    SELL("sell"),
    TAKE("take"),
    VAULT("vault"),
    RESELL("resell");

    private final String id;

    BanTypes(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static BanTypes byId(String s) {
        if (s == null) return null;
        String lower = s.toLowerCase(Locale.ROOT);
        for (BanTypes t : values()) {
            if (t.id.equals(lower)) return t;
        }
        return null;
    }

    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        for (BanTypes t : values()) {
            out.add(t.id);
        }
        return out;
    }
}
