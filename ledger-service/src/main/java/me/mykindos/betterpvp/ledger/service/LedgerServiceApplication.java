package me.mykindos.betterpvp.ledger.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.flywaydb.core.Flyway;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * The ledger service: the only writer of balances, reached over NATS.
 * <p>
 * Configured from the environment: {@code LEDGER_NATS_URL}, {@code LEDGER_DB_URL}, {@code LEDGER_DB_USER},
 * {@code LEDGER_DB_PASSWORD} and {@code LEDGER_THREADS}.
 */
public final class LedgerServiceApplication {

    private static final System.Logger LOG = System.getLogger(LedgerServiceApplication.class.getName());

    private LedgerServiceApplication() {
    }

    public static void main(String[] args) throws Exception {
        final int threads = Integer.parseInt(env("LEDGER_THREADS", "8"));
        final HikariDataSource dataSource = dataSource(env("LEDGER_DB_URL", "jdbc:postgresql://localhost:5432/ledger"),
                env("LEDGER_DB_USER", "ledger"), env("LEDGER_DB_PASSWORD", ""), threads);
        migrate(dataSource);

        final Connection nats = Nats.connect(new Options.Builder()
                .server(env("LEDGER_NATS_URL", Options.DEFAULT_URL))
                .maxReconnects(-1)
                .build());
        final NatsLedgerEndpoint endpoint = new NatsLedgerEndpoint(nats,
                new LedgerRequests(new PostgresLedgerStore(dataSource)), Executors.newFixedThreadPool(threads));
        endpoint.start();

        final CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            endpoint.close();
            try {
                nats.close();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            dataSource.close();
            stopped.countDown();
        }));
        LOG.log(System.Logger.Level.INFO, "Ledger service is answering on NATS");
        stopped.await();
    }

    public static @NotNull HikariDataSource dataSource(@NotNull String url, @NotNull String user,
                                                       @NotNull String password, int poolSize) {
        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        return new HikariDataSource(config);
    }

    public static void migrate(@NotNull HikariDataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/ledger")
                .load()
                .migrate();
    }

    private static @NotNull String env(@NotNull String key, @NotNull String fallback) {
        final String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
