package me.mykindos.betterpvp.metrics;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.nats.client.Connection;
import io.nats.client.Statistics;
import org.jetbrains.annotations.NotNull;

import java.util.function.ToDoubleFunction;

/**
 * Messages, bytes, drops and reconnects of one NATS connection, and whether it is connected.
 */
public final class NatsMetrics implements MeterBinder {

    private final Connection connection;

    public NatsMetrics(@NotNull Connection connection) {
        this.connection = connection;
    }

    @Override
    public void bindTo(@NotNull MeterRegistry registry) {
        counter(registry, "nats.messages.in", stats -> stats.getInMsgs());
        counter(registry, "nats.messages.out", stats -> stats.getOutMsgs());
        counter(registry, "nats.bytes.in", stats -> stats.getInBytes());
        counter(registry, "nats.bytes.out", stats -> stats.getOutBytes());
        counter(registry, "nats.messages.dropped", stats -> stats.getDroppedCount());
        counter(registry, "nats.reconnects", stats -> stats.getReconnects());
        Gauge.builder("nats.connected", connection, c -> c.getStatus() == Connection.Status.CONNECTED ? 1 : 0)
                .description("1 while the NATS connection is up")
                .register(registry);
    }

    private void counter(@NotNull MeterRegistry registry, @NotNull String name,
                         @NotNull ToDoubleFunction<Statistics> value) {
        FunctionCounter.builder(name, connection, c -> value.applyAsDouble(c.getStatistics())).register(registry);
    }
}
