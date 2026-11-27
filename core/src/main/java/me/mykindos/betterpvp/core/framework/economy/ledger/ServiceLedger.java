package me.mykindos.betterpvp.core.framework.economy.ledger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.gamer.properties.GamerProperty;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.ledger.LedgerPosting;
import me.mykindos.betterpvp.ledger.LedgerProtocol;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import me.mykindos.betterpvp.ledger.LedgerTransport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The ledger as the ledger service keeps it. Every posting is a request to the service, and a player's
 * {@code BALANCE} property is set to the balance the service answers with.
 * <p>
 * Answers arrive in any order, so each account remembers the sequence of the answer it last took and ignores older
 * ones.
 */
public class ServiceLedger implements Ledger, AutoCloseable {

    private final LedgerTransport transport;
    private final Executor mainThread;
    private final Cache<String, Long> sequences = Caffeine.newBuilder().expireAfterAccess(Duration.ofHours(6)).build();

    public ServiceLedger(@NotNull LedgerTransport transport, @NotNull Executor mainThread) {
        this.transport = transport;
        this.mainThread = mainThread;
    }

    @Override
    public @NotNull CompletableFuture<LedgerReceipt> post(@NotNull List<Movement> movements,
                                                          @Nullable String reference) {
        final LedgerPosting posting = Movement.posting(movements, reference);
        if (posting.problem() != null) {
            return CompletableFuture.completedFuture(LedgerReceipt.of(LedgerStatus.INVALID));
        }
        final Map<String, Gamer> gamers = new HashMap<>();
        for (Movement movement : movements) {
            track(movement.getFrom(), gamers);
            track(movement.getTo(), gamers);
        }

        final Map<String, String> payload = LedgerProtocol.encode(posting);
        CompletableFuture<LedgerReceipt> answer = transport.ask(LedgerProtocol.POST, payload);
        if (reference != null) {
            // A referenced posting lands at most once, so asking again after silence cannot move coins twice.
            answer = answer.thenCompose(receipt -> receipt.getStatus() == LedgerStatus.UNAVAILABLE
                    ? transport.ask(LedgerProtocol.POST, payload)
                    : CompletableFuture.completedFuture(receipt));
        }
        return answer.thenApplyAsync(receipt -> cache(receipt, gamers), mainThread);
    }

    @Override
    public @NotNull CompletableFuture<LedgerReceipt> open(@NotNull Gamer gamer) {
        final String account = LedgerAccounts.player(gamer.getUniqueId());
        return transport.ask(LedgerProtocol.OPEN, LedgerProtocol.encodeOpen(account, gamer.getBalance()))
                .thenApplyAsync(receipt -> cache(receipt, Map.of(account, gamer)), mainThread);
    }

    @Override
    public boolean isAvailable() {
        return transport.isAvailable();
    }

    @Override
    public void close() {
        transport.close();
    }

    private static void track(@NotNull LedgerParty party, @NotNull Map<String, Gamer> gamers) {
        if (party.getGamer() != null) {
            gamers.put(party.getAccount(), party.getGamer());
        }
    }

    private @NotNull LedgerReceipt cache(@NotNull LedgerReceipt receipt, @NotNull Map<String, Gamer> gamers) {
        if (!receipt.isPosted()) {
            return receipt;
        }
        receipt.getBalances().forEach((account, balance) -> {
            final Gamer gamer = gamers.get(account);
            final Long seen = sequences.getIfPresent(account);
            if (gamer == null || (seen != null && seen > receipt.getSequence())) {
                return;
            }
            sequences.put(account, receipt.getSequence());
            gamer.saveProperty(GamerProperty.BALANCE, (int) Math.min(Integer.MAX_VALUE, balance));
        });
        return receipt;
    }
}
