package me.mykindos.betterpvp.core.framework.economy.ledger;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.framework.net.NatsConnection;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import me.mykindos.betterpvp.ledger.LedgerTransport;
import me.mykindos.betterpvp.ledger.NatsLedgerTransport;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Picks the ledger from {@code core.ledger.mode}: {@code local} keeps balances on this server, {@code service} asks
 * the ledger service over the server's NATS connection.
 * <p>
 * A server set to {@code service} that cannot reach NATS still uses the service ledger, which then refuses every
 * movement. Falling back to a local ledger would let two servers each hold a different balance for one player.
 */
@Singleton
@CustomLog
public class LedgerProvider implements Provider<Ledger> {

    private final Core core;
    private final DatabaseLedgerJournal journal;
    private final NatsConnection nats;
    private volatile Ledger ledger;

    @Inject
    public LedgerProvider(@NotNull Core core, @NotNull DatabaseLedgerJournal journal, @NotNull NatsConnection nats) {
        this.core = core;
        this.journal = journal;
        this.nats = nats;
    }

    @Override
    public Ledger get() {
        if (ledger == null) {
            synchronized (this) {
                if (ledger == null) {
                    ledger = create();
                }
            }
        }
        return ledger;
    }

    private @NotNull Ledger create() {
        final String mode = core.getConfig().getOrSaveString("core.ledger.mode", "local");
        if (!"service".equalsIgnoreCase(mode)) {
            log.info("Keeping the ledger on this server").submit();
            return new InProcessLedger(journal);
        }

        final long timeoutMillis = core.getConfig().getOrSaveObject("core.ledger.timeout-ms", 2000L, Long.class);
        final String server = core.getConfig().getOrSaveString("core.info.server", "unknown");
        final LedgerTransport transport;
        if (nats.isUsable()) {
            transport = new NatsLedgerTransport(nats.getConnection(), nats.prefix(), server, Duration.ofMillis(timeoutMillis));
            log.info("Using the ledger service over NATS").submit();
        } else {
            transport = unreachable();
            log.error("The ledger is set to the service but NATS cannot be reached, so coins cannot move").submit();
        }
        return new ServiceLedger(transport, task -> UtilServer.runTask(core, task));
    }

    private static @NotNull LedgerTransport unreachable() {
        return new LedgerTransport() {
            @Override
            public @NotNull CompletableFuture<LedgerReceipt> ask(@NotNull String topic, @NotNull Map<String, String> payload) {
                return CompletableFuture.completedFuture(LedgerReceipt.of(LedgerStatus.UNAVAILABLE));
            }

            @Override
            public boolean isAvailable() {
                return false;
            }
        };
    }
}
