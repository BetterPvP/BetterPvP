package me.mykindos.betterpvp.clans.economy;

import me.mykindos.betterpvp.ledger.LedgerReceipt;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Sends a referenced posting again for as long as the ledger cannot be reached, so it ends with an answer the ledger
 * gave. The reference makes every attempt the same posting, which lands at most once.
 */
public final class PostingRetry {

    private static final Duration FIRST_WAIT = Duration.ofSeconds(1);
    private static final Duration LONGEST_WAIT = Duration.ofSeconds(30);

    private PostingRetry() {
    }

    /** Completes with the first answer that is not {@link LedgerStatus#UNAVAILABLE}. */
    public static @NotNull CompletableFuture<LedgerReceipt> untilAnswered(
            @NotNull Supplier<CompletableFuture<LedgerReceipt>> posting) {
        return attempt(posting, FIRST_WAIT);
    }

    private static @NotNull CompletableFuture<LedgerReceipt> attempt(
            @NotNull Supplier<CompletableFuture<LedgerReceipt>> posting, @NotNull Duration wait) {
        return posting.get().thenCompose(receipt -> {
            if (receipt.getStatus() != LedgerStatus.UNAVAILABLE) {
                return CompletableFuture.completedFuture(receipt);
            }
            final Duration next = wait.multipliedBy(2).compareTo(LONGEST_WAIT) > 0 ? LONGEST_WAIT : wait.multipliedBy(2);
            return CompletableFuture.supplyAsync(() -> null,
                            CompletableFuture.delayedExecutor(wait.toMillis(), TimeUnit.MILLISECONDS))
                    .thenCompose(ignored -> attempt(posting, next));
        });
    }
}
