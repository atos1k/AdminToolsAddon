package dev.atos1k.auc.util;

import dev.by1337.auc.auc.ClientAucLot;
import dev.by1337.auc.auc.sort.Sorting;
import dev.by1337.auc.handler.Auction;
import dev.by1337.auc.search.SearchResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class LotScanner {

    private LotScanner() {
    }

    private static final Sorting BY_UID = new Sorting("uid", Comparator.comparingInt(l -> l.lot.uid()));

    public static List<ClientAucLot> collectAllActive(Auction auction) {
        SearchResult result = auction.search(null, BY_UID);
        List<ClientAucLot> lots = new ArrayList<>();
        ClientAucLot lot;
        while ((lot = result.next()) != null) {
            lots.add(lot);
        }
        result.release();
        return lots;
    }

    public static List<ClientAucLot> collectOwnedBy(Auction auction, UUID owner) {
        SearchResult result = auction.search(owner, null, BY_UID);
        List<ClientAucLot> lots = new ArrayList<>();
        ClientAucLot lot;
        while ((lot = result.next()) != null) {
            lots.add(lot);
        }
        result.release();
        return lots;
    }
}
