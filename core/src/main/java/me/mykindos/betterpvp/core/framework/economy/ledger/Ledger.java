package me.mykindos.betterpvp.core.framework.economy.ledger;

import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Every movement of coins goes through here. A player's {@code BALANCE} property, and any other held account's
 * cached balance, is what the ledger last said it is.
 * <p>
 * Futures complete on the main thread. A ledger that can answer at once completes them before returning, so code
 * that reacts in {@code thenAccept} runs in the same tick as the call.
 */
public interface Ledger {

    /**
     * Lands every movement or none of them.
     *
     * @param reference names the posting so it lands at most once across every server, such as {@code auction:12}.
     *                  The same movements under the same reference answer posted again without moving anything.
     */
    @NotNull CompletableFuture<LedgerReceipt> post(@NotNull List<Movement> movements, @Nullable String reference);

    default @NotNull CompletableFuture<LedgerReceipt> move(@NotNull LedgerParty from, @NotNull LedgerParty to,
                                                           long amount, @NotNull String reason) {
        return post(List.of(new Movement(from, to, amount, reason)), null);
    }

    /** Brings a held account's cached balance in line with the ledger, which takes that balance if it has none. */
    @NotNull CompletableFuture<LedgerReceipt> open(@NotNull LedgerParty party);

    default @NotNull CompletableFuture<LedgerReceipt> open(@NotNull Gamer gamer) {
        return open(LedgerParty.of(gamer));
    }

    /** Whether a posting sent now could land. Anything a player should not lose to an outage asks first. */
    boolean isAvailable();
}
