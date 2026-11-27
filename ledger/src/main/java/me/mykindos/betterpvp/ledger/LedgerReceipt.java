package me.mykindos.betterpvp.ledger;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.OptionalLong;

/**
 * The answer to a posting.
 * <p>
 * {@code balances} holds the balance of every player account the posting touched, as of {@code sequence}. Sequences
 * only grow for an account, so a holder of a cached balance keeps whichever answer has the higher one.
 */
@Value
public class LedgerReceipt {

    @NotNull LedgerStatus status;
    @NotNull Map<String, Long> balances;
    long sequence;

    public static @NotNull LedgerReceipt of(@NotNull LedgerStatus status) {
        return new LedgerReceipt(status, Map.of(), 0);
    }

    public boolean isPosted() {
        return status.isPosted();
    }

    public @NotNull OptionalLong balance(@NotNull String account) {
        final Long balance = balances.get(account);
        return balance == null ? OptionalLong.empty() : OptionalLong.of(balance);
    }
}
