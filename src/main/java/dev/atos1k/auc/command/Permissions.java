package dev.atos1k.auc.command;

public final class Permissions {
    private Permissions() {
    }
    
    public static final String AHA = "aha.tools";
    public static final String BAUC = "bauc";
    public static final String TRANSACTIONS = AHA + ".transactions";
    public static final String ITEMHISTORY = AHA + ".itemhistory";
    public static final String EXPORT = AHA + ".export";
    public static final String LIQUID = AHA + ".liquid";
    public static final String STATS = AHA + ".stats";
    public static final String PRICE = AHA + ".price";
    public static final String FIND = AHA + ".find";
    public static final String SUSPICIOUS = AHA + ".suspicious";
    public static final String TREND = AHA + ".trend";
    public static final String TURNOVER = AHA + ".turnover";
    public static final String TOP = AHA + ".top";
    public static final String LOT = AHA + ".lot";
    public static final String REMOVELOT = AHA + ".removelot";
    public static final String GIVE = AHA + ".give";
    public static final String PLAYER = AHA + ".player";
    public static final String WIPE = AHA + ".wipe";
    public static final String WATCH = AHA + ".watch";
    public static final String ANNOUNCE = AHA + ".announce";
    public static final String BAN = AHA + ".ban";
    public static final String BLACKLIST = AHA + ".blacklist";
    public static final String ITEM = AHA + ".item";
    public static final String HOURS = AHA + ".hours";
    public static final String CONCENTRATION = AHA + ".concentration";
    public static final String LAUNDERING = AHA + ".laundering";
    public static final String OVERPRICED = AHA + ".overpriced";
    public static final String PRICERULE = AHA + ".pricerule";
    public static final String RATELIMIT = AHA + ".ratelimit";
    public static final String CHART = AHA + ".chart";
    public static final String DUMPALERT = AHA + ".dumpalert";
    public static final String BYPASS = AHA + ".bypass";
    public static final String ALERT = BAUC + ".alerts";
    
    public static final String[] CHILD = {
            TRANSACTIONS, ITEMHISTORY, EXPORT,
            LIQUID, STATS, PRICE, FIND, SUSPICIOUS,
            TREND, TURNOVER, TOP,
            LOT, REMOVELOT, GIVE,
            PLAYER, WIPE,
            WATCH, ANNOUNCE,
            BAN, BLACKLIST,
            ITEM, HOURS, CONCENTRATION, LAUNDERING, OVERPRICED,
            PRICERULE, RATELIMIT, CHART, DUMPALERT, BYPASS
    };
}