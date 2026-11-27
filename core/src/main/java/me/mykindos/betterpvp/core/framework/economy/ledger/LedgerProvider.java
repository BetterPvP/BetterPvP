package me.mykindos.betterpvp.core.framework.economy.ledger;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.ledger.NatsLedgerTransport;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;

/**
 * Picks the ledger from {@code core.ledger.mode}: {@code local} keeps balances on this server, {@code service} asks
 * the ledger service over NATS at {@code core.ledger.nats-url}.
 */
@Singleton
@CustomLog
public class LedgerProvider implements Provider<Ledger> {

    private final Core core;
    private final DatabaseLedgerJournal journal;
    private volatile Ledger ledger;

    @Inject
    public LedgerProvider(@NotNull Core core, @NotNull DatabaseLedgerJournal journal) {
        this.core = core;
        this.journal = journal;
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

    public void close() {
        if (ledger instanceof ServiceLedger service) {
            service.close();
        }
    }

    private @NotNull Ledger create() {
        final String mode = core.getConfig().getOrSaveString("core.ledger.mode", "local");
        if (!"service".equalsIgnoreCase(mode)) {
            log.info("Keeping the ledger on this server").submit();
            return new InProcessLedger(journal);
        }

        final String url = core.getConfig().getOrSaveString("core.ledger.nats-url", Options.DEFAULT_URL);
        final long timeoutMillis = core.getConfig().getOrSaveObject("core.ledger.timeout-ms", 2000L, Long.class);
        final String server = core.getConfig().getOrSaveString("core.info.server", "unknown");
        final Connection connection;
        try {
            connection = Nats.connectReconnectOnConnect(new Options.Builder()
                    .server(url)
                    .connectionName(server)
                    .maxReconnects(-1)
                    .build());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not reach the ledger's NATS at " + url, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reaching the ledger's NATS", ex);
        }
        log.info("Using the ledger service at {}", url).submit();
        return new ServiceLedger(new NatsLedgerTransport(connection, server, Duration.ofMillis(timeoutMillis)),
                task -> UtilServer.runTask(core, task));
    }
}
