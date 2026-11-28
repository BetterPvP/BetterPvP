package me.mykindos.betterpvp.metrics;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * A Micrometer registry with the JVM's memory, GC, thread and CPU meters, served in the Prometheus text format at
 * {@code /metrics}. Every meter carries the given common tags.
 */
public final class MetricsExporter implements AutoCloseable {

    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private final JvmGcMetrics gcMetrics = new JvmGcMetrics();
    private final HttpServer server;

    public MetricsExporter(int port, @NotNull Iterable<Tag> commonTags) throws IOException {
        registry.config().commonTags(commonTags);
        new ClassLoaderMetrics().bindTo(registry);
        new JvmMemoryMetrics().bindTo(registry);
        gcMetrics.bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new UptimeMetrics().bindTo(registry);

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/metrics", exchange -> {
            final byte[] body = registry.scrape().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    /**
     * A standalone service's exporter, on {@code METRICS_PORT} (9464 by default) and tagged with its name.
     */
    public static @NotNull MetricsExporter forService(@NotNull String service) throws IOException {
        final String port = System.getenv("METRICS_PORT");
        return new MetricsExporter(port == null || port.isBlank() ? 9464 : Integer.parseInt(port),
                Tags.of("service", service));
    }

    public @NotNull MeterRegistry getRegistry() {
        return registry;
    }

    @Override
    public void close() {
        server.stop(0);
        gcMetrics.close();
        registry.close();
    }
}
