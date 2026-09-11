package dev.atos1k.auc.util;

import dev.by1337.auc.common.auc.log.impl.AddLotLog;
import dev.by1337.auc.common.auc.log.impl.BuyAuctionLog;
import dev.by1337.auc.common.auc.log.impl.LotExpirationLog;
import dev.by1337.auc.common.auc.log.impl.TakeLotLog;
import dev.by1337.auc.common.auc.log.impl.TakeVaultLog;
import dev.by1337.auc.common.auc.log.impl.VaultLotExpirationLog;
import java.util.Map;

public final class LogTypes {

    private LogTypes() {
    }

    public static final Map<String, String> TYPE_ALIASES = Map.of(
            "buy", BuyAuctionLog.ID,
            "add", AddLotLog.ID,
            "take", TakeLotLog.ID,
            "takevault", TakeVaultLog.ID,
            "expire", LotExpirationLog.ID,
            "expirevault", VaultLotExpirationLog.ID
    );

    public static String describe(String id) {
        return switch (id) {
            case BuyAuctionLog.ID -> "Покупка";
            case AddLotLog.ID -> "Выставление";
            case TakeLotLog.ID -> "Снятие лота";
            case TakeVaultLog.ID -> "Забор из vault";
            case LotExpirationLog.ID -> "Истёк лот";
            case VaultLotExpirationLog.ID -> "Истёк vault";
            default -> id;
        };
    }
}
