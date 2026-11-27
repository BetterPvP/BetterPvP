package me.mykindos.betterpvp.core.framework.economy.ledger;

import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.ledger.LedgerPosting;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ledger for a server that is the whole economy. Balances stay where each holder caches them, such as a gamer's
 * {@code BALANCE} property, and every posting is journalled. Answers at once, on the calling thread.
 */
public class InProcessLedger implements Ledger {

    private final LedgerJournal journal;
    private final Map<String, String> references = new ConcurrentHashMap<>();

    public InProcessLedger(@NotNull LedgerJournal journal) {
        this.journal = journal;
    }

    @Override
    public synchronized @NotNull CompletableFuture<LedgerReceipt> post(@NotNull List<Movement> movements,
                                                                       @Nullable String reference) {
        return CompletableFuture.completedFuture(apply(movements, reference));
    }

    @Override
    public @NotNull CompletableFuture<LedgerReceipt> open(@NotNull LedgerParty party) {
        final LedgerBalance balance = party.getBalance();
        if (balance == null) {
            return CompletableFuture.completedFuture(LedgerReceipt.of(LedgerStatus.INVALID));
        }
        return CompletableFuture.completedFuture(
                new LedgerReceipt(LedgerStatus.POSTED, Map.of(party.getAccount(), balance.get()), 0));
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private @NotNull LedgerReceipt apply(@NotNull List<Movement> movements, @Nullable String reference) {
        final LedgerPosting posting = Movement.posting(movements, reference);
        if (posting.problem() != null) {
            return LedgerReceipt.of(LedgerStatus.INVALID);
        }

        final Map<String, LedgerBalance> holders = new LinkedHashMap<>();
        final Map<String, Long> balances = new LinkedHashMap<>();
        for (Movement movement : movements) {
            if (!collect(movement.getFrom(), -movement.getAmount(), holders, balances)
                    || !collect(movement.getTo(), movement.getAmount(), holders, balances)) {
                return LedgerReceipt.of(LedgerStatus.INVALID);
            }
        }

        if (reference != null) {
            final String landed = references.get(reference);
            if (landed != null && !landed.equals(posting.fingerprint())) {
                return LedgerReceipt.of(LedgerStatus.DUPLICATE);
            }
            if (landed != null) {
                final Map<String, Long> current = new HashMap<>();
                holders.forEach((account, holder) -> current.put(account, holder.get()));
                return new LedgerReceipt(LedgerStatus.POSTED, Map.copyOf(current), 0);
            }
        }

        for (long balance : balances.values()) {
            if (balance < 0) {
                return LedgerReceipt.of(LedgerStatus.INSUFFICIENT_FUNDS);
            }
            if (balance > Integer.MAX_VALUE) {
                return LedgerReceipt.of(LedgerStatus.INVALID);
            }
        }

        balances.forEach((account, balance) -> holders.get(account).set(balance));
        if (reference != null) {
            references.put(reference, posting.fingerprint());
        }
        journal.record(posting);
        return new LedgerReceipt(LedgerStatus.POSTED, Map.copyOf(balances), 0);
    }

    /**
     * Adds {@code delta} to the party's running balance, answering false for a held account with nothing on this
     * server to hold it.
     */
    private static boolean collect(@NotNull LedgerParty party, long delta, @NotNull Map<String, LedgerBalance> holders,
                                   @NotNull Map<String, Long> balances) {
        final LedgerBalance holder = party.getBalance();
        if (holder == null) {
            return !LedgerAccounts.isHeld(party.getAccount());
        }
        holders.putIfAbsent(party.getAccount(), holder);
        balances.merge(party.getAccount(), holder.get() + delta, (running, ignored) -> running + delta);
        return true;
    }
}
