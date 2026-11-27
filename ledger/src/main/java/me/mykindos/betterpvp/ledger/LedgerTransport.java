package me.mykindos.betterpvp.ledger;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Carries one request to the ledger service and brings back its one answer.
 * <p>
 * The future always completes normally. A request nobody answered in time completes with
 * {@link LedgerStatus#UNAVAILABLE}.
 */
public interface LedgerTransport {

    @NotNull CompletableFuture<LedgerReceipt> ask(@NotNull String topic, @NotNull Map<String, String> payload);

    /** Whether a request sent now could reach the service. */
    boolean isAvailable();
}
