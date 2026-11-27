package me.mykindos.betterpvp.clans.clans.writer;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.clans.clans.core.storage.ClanInventories;
import me.mykindos.betterpvp.clans.clans.core.storage.ClanLocks;
import me.mykindos.betterpvp.clans.clans.core.storage.LocalClanInventories;
import me.mykindos.betterpvp.clans.clans.core.storage.LockAnswer;
import me.mykindos.betterpvp.clans.clans.core.storage.NatsClanLocks;
import me.mykindos.betterpvp.clans.clans.core.storage.ServiceClanInventories;
import me.mykindos.betterpvp.core.framework.net.NatsConnection;
import me.mykindos.betterpvp.core.framework.role.ServerRole;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Chooses how clans and their coins are written. A server with role {@code all} is a network of one and writes them
 * itself. Every other role writes through the Clans service and the ledger service, eagerly so it is listening for
 * changes from the start, and locks clan vaults and mailboxes across every server.
 */
@CustomLog
public class ClanWritingModule extends AbstractModule {

    private final ServerRole role;

    public ClanWritingModule(@NotNull ServerRole role) {
        this.role = role;
    }

    @Override
    protected void configure() {
        if (role == ServerRole.ALL) {
            bind(ClanWriter.class).to(LocalClanWriter.class);
            bind(ClanBank.class).to(LocalClanBank.class);
            bind(ClanInventories.class).to(LocalClanInventories.class);
        } else {
            bind(ClanWriter.class).to(ServiceClanWriter.class).asEagerSingleton();
            bind(ClanBank.class).to(ServiceClanBank.class).asEagerSingleton();
            bind(ClanInventories.class).to(ServiceClanInventories.class);
        }
    }

    /** Only asked for by a networked server. One that cannot reach NATS keeps every vault and mailbox closed. */
    @Provides
    @Singleton
    ClanLocks locks(@NotNull NatsConnection nats) {
        final NatsClanLocks locks = nats.isUsable()
                ? NatsClanLocks.open(nats.getConnection(), nats.prefix(), nats.replicas(), Duration.ofSeconds(30))
                : null;
        if (locks != null) {
            return locks;
        }
        log.error("Clan locks cannot be reached, so clan vaults and mailboxes stay closed on this server").submit();
        return new ClanLocks() {
            @Override
            public @NotNull CompletableFuture<LockAnswer> take(@NotNull String key, @NotNull String holder) {
                return CompletableFuture.completedFuture(LockAnswer.unreachable());
            }

            @Override
            public void release(@NotNull String key) {
            }
        };
    }
}
