package me.mykindos.betterpvp.ledger;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LedgerProtocolTest {

    @Test
    void postingSurvivesTheWire() {
        final String buyer = LedgerAccounts.player(UUID.randomUUID());
        final LedgerPosting posting = new LedgerPosting(List.of(
                new LedgerEntry(buyer, LedgerAccounts.AUCTION_HOUSE, 500, "auction.buy"),
                new LedgerEntry(LedgerAccounts.SHOP, buyer, 20, "shop.sell")), "auction:7");

        assertEquals(posting, LedgerProtocol.decodePosting(LedgerProtocol.encode(posting)));
    }

    @Test
    void receiptSurvivesTheWire() {
        final LedgerReceipt receipt = new LedgerReceipt(LedgerStatus.POSTED, Map.of("player:a", 40L), 12);

        assertEquals(receipt, LedgerProtocol.decodeReceipt(LedgerProtocol.encode(receipt)));
    }
}
