package me.mykindos.betterpvp.clans.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import me.mykindos.betterpvp.metrics.MetricsExporter;
import me.mykindos.betterpvp.metrics.NatsMetrics;
import me.mykindos.betterpvp.network.protocol.Subjects;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

/**
 * Starts the Clans service from environment variables: {@code NATS_URL}, {@code NATS_TOKEN} (optional),
 * {@code NATS_PREFIX} (the servers' {@code core.nats.prefix}, {@code bpvp} by default), {@code DATABASE_URL} (a JDBC
 * URL), {@code DATABASE_USER} and {@code DATABASE_PASSWORD}. The clan tables are created by the clans plugin's
 * migrations. Metrics are served on {@code METRICS_PORT}.
 */
public final class ClansServiceApplication {

    private static final System.Logger LOG = System.getLogger(ClansServiceApplication.class.getName());

    private ClansServiceApplication() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(required("DATABASE_URL"));
        hikari.setUsername(required("DATABASE_USER"));
        hikari.setPassword(required("DATABASE_PASSWORD"));
        hikari.setMaximumPoolSize(2);
        final HikariDataSource dataSource = new HikariDataSource(hikari);

        final Options.Builder options = new Options.Builder()
                .server(System.getenv().getOrDefault("NATS_URL", Options.DEFAULT_URL))
                .connectionName("clans-service")
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(1));
        final String token = System.getenv("NATS_TOKEN");
        if (token != null && !token.isBlank()) {
            options.token(token.toCharArray());
        }
        final Connection nats = Nats.connect(options.build());
        final MetricsExporter metrics = MetricsExporter.forService("clans-service");
        new NatsMetrics(nats).bindTo(metrics.getRegistry());

        final String prefix = System.getenv().getOrDefault("NATS_PREFIX", Subjects.DEFAULT_PREFIX);
        final ClanService service = new ClanService(nats, new ClanStore(dataSource), prefix);
        service.start();
        LOG.log(System.Logger.Level.INFO, "Clans service answering on " + nats.getConnectedUrl());

        final CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            service.stop();
            try {
                nats.close();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            dataSource.close();
            metrics.close();
            stopped.countDown();
        }, "clans-service-shutdown"));
        stopped.await();
    }

    private static @NotNull String required(@NotNull String variable) {
        final String value = System.getenv(variable);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variable + " is not set");
        }
        return value;
    }
}
