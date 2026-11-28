package me.mykindos.betterpvp.core.framework.economy.ledger;

import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.gamer.properties.GamerProperty;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.ledger.LedgerPosting;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InProcessLedgerTest {

    private final List<LedgerPosting> journal = new ArrayList<>();
    private final InProcessLedger ledger = new InProcessLedger(journal::add);

    @Test
    void aTransferAnswersAtOnceAndMovesBothBalances() {
        final Gamer from = gamer(500);
        final Gamer to = gamer(20);

        final var answer = ledger.move(LedgerParty.of(from), LedgerParty.of(to), 200, "trade");

        assertTrue(answer.isDone());
        assertTrue(answer.join().isPosted());
        verify(from).saveProperty(GamerProperty.BALANCE, 300);
        verify(to).saveProperty(GamerProperty.BALANCE, 220);
        assertEquals(1, journal.size());
    }

    @Test
    void aBatchThatOverdrawsAnyoneMovesNothing() {
        final Gamer buyer = gamer(100);
        final List<Movement> movements = List.of(
                new Movement(LedgerParty.system(LedgerAccounts.SHOP), LedgerParty.of(buyer), 50, "shop.sell"),
                new Movement(LedgerParty.of(buyer), LedgerParty.system(LedgerAccounts.SHOP), 151, "shop.buy"));

        final LedgerReceipt receipt = ledger.post(movements, null).join();

        assertEquals(LedgerStatus.INSUFFICIENT_FUNDS, receipt.getStatus());
        verify(buyer, never()).saveProperty(any(GamerProperty.class), any());
        assertTrue(journal.isEmpty());
    }

    @Test
    void aReferenceLandsOnceAndARetryMovesNothing() {
        final Gamer first = gamer(1_000);
        final Gamer second = gamer(1_000);
        final Movement firstBuys = new Movement(LedgerParty.of(first), LedgerParty.system(LedgerAccounts.AUCTION_HOUSE), 600, "auction.buy");
        final Movement secondBuys = new Movement(LedgerParty.of(second), LedgerParty.system(LedgerAccounts.AUCTION_HOUSE), 600, "auction.buy");

        assertTrue(ledger.post(List.of(firstBuys), "auction:1").join().isPosted());
        assertEquals(LedgerStatus.DUPLICATE, ledger.post(List.of(secondBuys), "auction:1").join().getStatus());
        assertTrue(ledger.post(List.of(firstBuys), "auction:1").join().isPosted());

        verify(second, never()).saveProperty(any(GamerProperty.class), any());
        assertEquals(1, journal.size());
    }

    private static Gamer gamer(int balance) {
        final Gamer gamer = mock(Gamer.class);
        when(gamer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(gamer.getBalance()).thenReturn(balance);
        return gamer;
    }
}
