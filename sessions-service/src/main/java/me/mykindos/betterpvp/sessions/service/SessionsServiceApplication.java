package me.mykindos.betterpvp.sessions.service;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import me.mykindos.betterpvp.network.protocol.Subjects;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * The sessions service. It decides which server owns each player and which proxy each account is on, reached over
 * NATS.
 * <p>
 * Configured from the environment: {@code SESSIONS_NATS_URL}, {@code SESSIONS_NATS_TOKEN}, {@code SESSIONS_NATS_PREFIX},
 * {@code SESSIONS_LEASE_MILLIS}, {@code SESSIONS_PROXY_TIMEOUT_MILLIS}, {@code SESSIONS_MAX_WAIT_MILLIS} and
 * {@code SESSIONS_THREADS}.
 */
public final class SessionsServiceApplication {

    private static final System.Logger LOG = System.getLogger(SessionsServiceApplication.class.getName());

    private SessionsServiceApplication() {
    }

    public static void main(String[] args) throws Exception {
        final Options.Builder options = new Options.Builder()
                .server(env("SESSIONS_NATS_URL", Options.DEFAULT_URL))
                .maxReconnects(-1);
        final String token = env("SESSIONS_NATS_TOKEN", "");
        if (!token.isBlank()) {
            options.token(token.toCharArray());
        }
        final Connection nats = Nats.connect(options.build());

        final LeaseBook book = new LeaseBook(Long.parseLong(env("SESSIONS_LEASE_MILLIS", "15000")),
                Long.parseLong(env("SESSIONS_PROXY_TIMEOUT_MILLIS", "15000")), System::currentTimeMillis);
        final SessionEndpoint endpoint = new SessionEndpoint(nats, env("SESSIONS_NATS_PREFIX", Subjects.DEFAULT_PREFIX),
                book, Executors.newScheduledThreadPool(Integer.parseInt(env("SESSIONS_THREADS", "4"))),
                Long.parseLong(env("SESSIONS_MAX_WAIT_MILLIS", "30000")));
        endpoint.start();

        final CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            endpoint.close();
            try {
                nats.close();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            stopped.countDown();
        }));
        LOG.log(System.Logger.Level.INFO, "Sessions service is answering on NATS");
        stopped.await();
    }

    private static @NotNull String env(@NotNull String key, @NotNull String fallback) {
        final String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
