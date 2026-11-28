package me.mykindos.betterpvp.core.playerstate;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.framework.net.NatsConnection;
import me.mykindos.betterpvp.core.playerstate.session.LocalPlayerLeases;
import me.mykindos.betterpvp.core.playerstate.session.NatsPlayerLeases;
import me.mykindos.betterpvp.core.playerstate.session.PlayerLeases;
import me.mykindos.betterpvp.sessions.SessionCalls;

import java.time.Duration;

/**
 * Wiring for player state. {@code core.network.sessions} names how players are shared, like the other
 * {@code core.network} seams: {@code nats} shares them through the sessions service, and {@code local}, the default,
 * keeps everyone on this server.
 */
@CustomLog
public class PlayerStateModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(PlayerStateStore.class).to(DatabasePlayerStateStore.class);
    }

    @Provides
    @Singleton
    PlayerLeases leases(Core core, NatsConnection nats) {
        if (!"nats".equalsIgnoreCase(core.getConfig().getOrSaveString("core.network.sessions", "local"))) {
            return new LocalPlayerLeases();
        }
        if (!nats.isUsable()) {
            log.warn("Sessions are set to NATS but NATS is not reachable, so players are not shared").submit();
            return new LocalPlayerLeases();
        }

        final String server = Core.getCurrentRealm().getServer().getName();
        return new NatsPlayerLeases(new SessionCalls(nats.getConnection(), nats.prefix(), server), Duration.ofSeconds(5));
    }
}
