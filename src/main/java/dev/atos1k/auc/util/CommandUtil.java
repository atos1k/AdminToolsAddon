package dev.atos1k.auc.util;

import org.bukkit.Material;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CommandUtil {

    private CommandUtil() {
    }

    public static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (char c : s.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }

    public static Long parseLongOrNull(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double parseDoubleOrNull(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static List<String> filterStartsWith(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) out.add(o);
        }
        return out;
    }

    public static List<String> materialNames() {
        List<String> out = new ArrayList<>();
        for (Material m : Material.values()) {
            if (!m.isLegacy() && m.isItem()) out.add(m.name());
        }
        return out;
    }
}
