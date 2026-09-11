package dev.atos1k.auc.command;


public final class Permissions {

    private Permissions() {
    }

    public static final String BASE = "bauc.admin";
    public static final String TRANSACTIONS = BASE + ".transactions";
    public static final String ITEMHISTORY = BASE + ".itemhistory";
    public static final String EXPORT = BASE + ".export";
    public static final String LIQUID = BASE + ".liquid";
    public static final String STATS = BASE + ".stats";
    public static final String PRICE = BASE + ".price";
    public static final String FIND = BASE + ".find";
    public static final String SUSPICIOUS = BASE + ".suspicious";
    public static final String TREND = BASE + ".trend";
    public static final String TURNOVER = BASE + ".turnover";
    public static final String TOP = BASE + ".top";
    public static final String LOT = BASE + ".lot";
    public static final String REMOVELOT = BASE + ".removelot";
    public static final String GIVE = BASE + ".give";
    public static final String PLAYER = BASE + ".player";
    public static final String WIPE = BASE + ".wipe";
    public static final String WATCH = BASE + ".watch";
    public static final String ANNOUNCE = BASE + ".announce";
    
    public static final String[] ALL = {
            BASE,
            TRANSACTIONS, ITEMHISTORY, EXPORT,
            LIQUID, STATS, PRICE, FIND, SUSPICIOUS,
            TREND, TURNOVER, TOP,
            LOT, REMOVELOT, GIVE,
            PLAYER, WIPE,
            WATCH, ANNOUNCE
    };
}
