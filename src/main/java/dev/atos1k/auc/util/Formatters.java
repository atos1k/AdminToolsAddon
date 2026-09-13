package dev.atos1k.auc.util;

import dev.by1337.auc.util.number.EconomyUtil;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class Formatters {
    private Formatters() {
    }

    private static final ThreadLocal<DecimalFormat> PRICE = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));
    private static final ThreadLocal<SimpleDateFormat> DATE = ThreadLocal.withInitial(() -> new SimpleDateFormat("dd.MM.yyyy HH:mm:ss"));

    public static String money(double value) {
        return PRICE.get().format(value);
    }

    public static String moneyFromCents(long cents) {
        return PRICE.get().format(EconomyUtil.fromCents(cents));
    }

    public static String date(long millis) {
        return DATE.get().format(new Date(millis));
    }
}
