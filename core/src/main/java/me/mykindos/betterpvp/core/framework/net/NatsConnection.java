package me.mykindos.betterpvp.core.framework.net;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import lombok.CustomLog;
import lombok.Getter;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.network.protocol.Subjects;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;

/**
 * The one connection to NATS, shared by the bus and the directory.
 * <p>
 * Connecting is tried once, at the first thing that asks. Once connected, the client reconnects by itself for as
 * long as the server runs.
 */
@Singleton
@CustomLog
public class NatsConnection {

    private final Core core;

    @Getter
    private Connection connection;

    private boolean attempted;

    @Inject
    public NatsConnection(@NotNull Core core) {
        this.core = core;
    }

    /** Whether NATS is configured and answered. */
    public synchronized boolean isUsable() {
        if (!attempted) {
            attempted = true;
            connect();
        }
        return connection != null && connection.getStatus() != Connection.Status.CLOSED;
    }

    /** What every subject and bucket name starts with, so networks sharing a NATS cluster stay apart. */
    public @NotNull String prefix() {
        return core.getConfig().getOrSaveString("core.nats.prefix", Subjects.DEFAULT_PREFIX);
    }

    /** How many NATS nodes hold a copy of each directory bucket. */
    public int replicas() {
        return core.getConfig().getOrSaveObject("core.nats.replicas", 1, Integer.class);
    }

    public synchronized void close() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        connection = null;
    }

    private void connect() {
        if (!core.getConfig().getOrSaveObject("core.nats.enabled", false, Boolean.class)) {
            return;
        }

        final String url = core.getConfig().getOrSaveString("core.nats.url", "nats://127.0.0.1:4222");
        final String token = core.getConfig().getOrSaveString("core.nats.token", "");

        final Options.Builder options = Options.builder()
                .server(url)
                .connectionTimeout(Duration.ofSeconds(2))
                .maxReconnects(-1);
        if (!token.isBlank()) {
            options.token(token.toCharArray());
        }

        try {
            connection = Nats.connect(options.build());
            log.info("Connected to NATS at {}", url).submit();
        } catch (IOException exception) {
            log.warn("Could not reach NATS at {}, carrying on without it", url, exception).submit();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
