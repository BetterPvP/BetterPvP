package me.mykindos.betterpvp.ledger.service;

import me.mykindos.betterpvp.ledger.LedgerProtocol;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** Answers ledger requests from their payloads, whatever carried them. */
public class LedgerRequests {

    private static final System.Logger LOG = System.getLogger(LedgerRequests.class.getName());

    private final LedgerStore store;

    public LedgerRequests(@NotNull LedgerStore store) {
        this.store = store;
    }

    public @NotNull Map<String, String> answer(@NotNull String topic, @NotNull Map<String, String> payload) {
        return LedgerProtocol.encode(receipt(topic, payload));
    }

    private @NotNull LedgerReceipt receipt(@NotNull String topic, @NotNull Map<String, String> payload) {
        try {
            return switch (topic) {
                case LedgerProtocol.POST -> store.post(LedgerProtocol.decodePosting(payload));
                case LedgerProtocol.OPEN -> store.open(LedgerProtocol.openAccount(payload),
                        LedgerProtocol.openingBalance(payload));
                default -> LedgerReceipt.of(LedgerStatus.INVALID);
            };
        } catch (IllegalArgumentException ex) {
            return LedgerReceipt.of(LedgerStatus.INVALID);
        } catch (IllegalStateException ex) {
            LOG.log(System.Logger.Level.ERROR, "Could not answer " + topic, ex);
            return LedgerReceipt.of(LedgerStatus.UNAVAILABLE);
        }
    }
}
