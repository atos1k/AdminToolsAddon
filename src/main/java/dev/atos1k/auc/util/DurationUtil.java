package dev.atos1k.auc.util;

import java.util.Locale;

public final class DurationUtil {
    private DurationUtil() {
    }

    public static long parse(String s) {
        if (s == null || s.isBlank()) return 0;
        String lower = s.toLowerCase(Locale.ROOT).trim();
        if (lower.equals("perm") || lower.equals("permanent") || lower.equals("forever")) {
            return 0;
        }
        long total = 0;
        long number = 0;
        boolean hasDigits = false;
        boolean hasUnit = false;
        for (char c : lower.toCharArray()) {
            if (Character.isDigit(c)) {
                number = number * 10 + (c - '0');
                hasDigits = true;
                continue;
            }
            if (!hasDigits) return -1;
            long unit = switch (c) {
                case 's' -> 1000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                case 'w' -> 604_800_000L;
                default -> -1L;
            };
            if (unit < 0) return -1;
            total += number * unit;
            number = 0;
            hasDigits = false;
            hasUnit = true;
        }
        if (hasDigits && !hasUnit) return number * 3_600_000L;
        if (hasDigits) return -1;
        return total;
    }

    public static String format(long millis) {
        if (millis <= 0) return "0с";
        long days = millis / 86_400_000L;
        long hours = (millis % 86_400_000L) / 3_600_000L;
        long minutes = (millis % 3_600_000L) / 60_000L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("д ");
        if (hours > 0) sb.append(hours).append("ч ");
        if (minutes > 0 || sb.isEmpty()) sb.append(Math.max(minutes, 1)).append("м");
        return sb.toString().trim();
    }
}
