package me.mykindos.betterpvp.proxy.network;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import me.mykindos.betterpvp.network.protocol.PlayerRoster;
import me.mykindos.betterpvp.network.protocol.PresenceMessages;
import me.mykindos.betterpvp.proxy.ProxyConfig;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Duration;

/** Connects this proxy to the network and keeps it announced until the proxy stops. */
public final class ProxyNetwork {

    private final Connection connection;
    private final ProxyBus bus;
    private final NetworkLogins logins;

    private ProxyNetwork(Connection connection, ProxyBus bus, NetworkLogins logins) {
        this.connection = connection;
        this.bus = bus;
        this.logins = logins;
    }

    public static ProxyNetwork start(Object plugin, ProxyServer proxy, ProxyConfig config, Logger logger)
            throws IOException {
        final Options.Builder options = Options.builder()
                .server(config.natsUrl())
                .connectionName(config.proxyName())
                .maxReconnects(-1);
        if (!config.natsToken().isBlank()) {
            options.token(config.natsToken().toCharArray());
        }

        final Connection connection;
        try {
            connection = Nats.connect(options.build());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted connecting to NATS", interrupted);
        }

        final ProxyBus bus = new ProxyBus(connection, config.networkPrefix(), config.proxyName(), logger);
        // Three missed rosters and a proxy is taken to be gone.
        final PlayerPresence presence = new PlayerPresence(bus, new PlayerRoster(
                Duration.ofSeconds(PresenceMessages.ROSTER_INTERVAL_SECONDS * 3L), System::currentTimeMillis));
        presence.start();

        final NetworkLogins logins = new NetworkLogins(new OpenSessions(), presence, logger);
        final ProxyNetwork network = new ProxyNetwork(connection, bus, logins);
        proxy.getEventManager().register(plugin, new NetworkListener(proxy, logins,
                new HubRouter(bus, config.hubSite(), logger), logger));
        proxy.getEventManager().register(plugin, network);
        proxy.getScheduler().buildTask(plugin, logins::heartbeat)
                .repeat(Duration.ofSeconds(PresenceMessages.ROSTER_INTERVAL_SECONDS))
                .schedule();

        logger.info("Proxy {} joined the network at {}", config.proxyName(), config.natsUrl());
        return network;
    }

    /** Lets every player go before NATS closes, so they can reconnect through another proxy straight away. */
    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        logins.shutdown();
        bus.flush(Duration.ofSeconds(2));
        bus.close();
        try {
            connection.close();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
