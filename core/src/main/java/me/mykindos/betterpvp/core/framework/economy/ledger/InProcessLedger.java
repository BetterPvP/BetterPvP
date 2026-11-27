package me.mykindos.betterpvp.core.framework.economy.ledger;

import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.gamer.properties.GamerProperty;
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
 * The ledger for a server that is the whole economy. Balances stay in each gamer's {@code BALANCE} property and
 * every posting is journalled. Answers at once, on the calling thread.
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
    public @NotNull CompletableFuture<LedgerReceipt> open(@NotNull Gamer gamer) {
        final String account = LedgerAccounts.player(gamer.getUniqueId());
        return CompletableFuture.completedFuture(
                new LedgerReceipt(LedgerStatus.POSTED, Map.of(account, (long) gamer.getBalance()), 0));
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

        final Map<Gamer, Long> balances = new LinkedHashMap<>();
        for (Movement movement : movements) {
            if (!collect(movement.getFrom(), -movement.getAmount(), balances)
                    || !collect(movement.getTo(), movement.getAmount(), balances)) {
                return LedgerReceipt.of(LedgerStatus.INVALID);
            }
        }

        if (reference != null) {
            final String landed = references.get(reference);
            if (landed != null && !landed.equals(posting.fingerprint())) {
                return LedgerReceipt.of(LedgerStatus.DUPLICATE);
            }
            if (landed != null) {
                final Map<Gamer, Long> current = new LinkedHashMap<>();
                balances.keySet().forEach(gamer -> current.put(gamer, (long) gamer.getBalance()));
                return receipt(current);
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

        balances.forEach((gamer, balance) -> gamer.saveProperty(GamerProperty.BALANCE, (int) (long) balance));
        if (reference != null) {
            references.put(reference, posting.fingerprint());
        }
        journal.record(posting);
        return receipt(balances);
    }

    /** Adds {@code delta} to the party's running balance, answering false for a player with no gamer to hold it. */
    private static boolean collect(@NotNull LedgerParty party, long delta, @NotNull Map<Gamer, Long> balances) {
        final Gamer gamer = party.getGamer();
        if (gamer == null) {
            return !LedgerAccounts.isPlayer(party.getAccount());
        }
        balances.merge(gamer, gamer.getBalance() + delta, (running, ignored) -> running + delta);
        return true;
    }

    private static @NotNull LedgerReceipt receipt(@NotNull Map<Gamer, Long> balances) {
        final Map<String, Long> byAccount = new HashMap<>();
        balances.forEach((gamer, balance) -> byAccount.put(LedgerAccounts.player(gamer.getUniqueId()), balance));
        return new LedgerReceipt(LedgerStatus.POSTED, Map.copyOf(byAccount), 0);
    }
}
