package dev.atos1k.auc.util;

import dev.by1337.auc.auc.ClientAucLot;
import dev.by1337.auc.handler.Auction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;

public final class LotScanner {
    private LotScanner() {
    }

    public static List<ClientAucLot> collectAllActive(Auction auction) {
        return new ArrayList<>(auction.lotsSet());
    }

    public static List<ClientAucLot> collectByMaterial(Auction auction, Material material) {
        return new ArrayList<>(auction.lotsSetByMaterial(material.ordinal(), null));
    }

    public static List<ClientAucLot> collectOwnedBy(Auction auction, UUID owner) {
        List<ClientAucLot> lots = new ArrayList<>();
        for (ClientAucLot lot : auction.lotsSet()) {
            if (lot.isOwner(owner)) lots.add(lot);
        }
        return lots;
    }
}
