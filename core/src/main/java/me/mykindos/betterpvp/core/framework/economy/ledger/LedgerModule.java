package me.mykindos.betterpvp.core.framework.economy.ledger;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.framework.net.NatsConnection;
import me.mykindos.betterpvp.core.framework.role.RoleGate;
import me.mykindos.betterpvp.core.framework.role.ServerRole;
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
 * Wiring for the ledger, and the only place its implementation is chosen. A server with role {@code all} is the
 * whole economy and keeps its own ledger. Every other role asks the ledger service over the server's NATS
 * connection.
 * <p>
 * A networked server that cannot reach NATS still uses the service ledger, which then refuses every movement. A
 * local ledger there would let two servers each hold a different balance for one player.
 */
@CustomLog
public class LedgerModule extends AbstractModule {

    @Provides
    @Singleton
    Ledger ledger(@NotNull RoleGate roles, @NotNull DatabaseLedgerJournal journal, @NotNull NatsConnection nats,
                  @NotNull Core core) {
        if (roles.getRole() == ServerRole.ALL) {
            return new InProcessLedger(journal);
        }

        final LedgerTransport transport;
        if (nats.isUsable()) {
            transport = new NatsLedgerTransport(nats.getConnection(), nats.prefix(),
                    core.getConfig().getOrSaveString("core.info.server", "unknown"), Duration.ofSeconds(2));
        } else {
            transport = unreachable();
            log.error("NATS cannot be reached, so coins cannot move on this server").submit();
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
