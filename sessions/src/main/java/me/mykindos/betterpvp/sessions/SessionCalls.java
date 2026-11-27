package me.mykindos.betterpvp.sessions;

import io.nats.client.Connection;
import me.mykindos.betterpvp.network.protocol.EnvelopeCodec;
import me.mykindos.betterpvp.network.protocol.NetworkEnvelope;
import me.mykindos.betterpvp.network.protocol.Subjects;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Sends requests to the sessions service and reads its answer, or publishes to it when no answer is wanted. */
public class SessionCalls {

    private final Connection connection;
    private final String prefix;
    private final String origin;
    private final EnvelopeCodec codec = new EnvelopeCodec();

    public SessionCalls(@NotNull Connection connection, @NotNull String prefix, @NotNull String origin) {
        this.connection = connection;
        this.prefix = prefix;
        this.origin = origin;
    }

    public @NotNull String origin() {
        return origin;
    }

    /** Completes with the service's payload, or exceptionally when it did not answer within {@code timeout}. */
    public @NotNull CompletableFuture<Map<String, String>> ask(@NotNull String topic, @NotNull Map<String, String> payload,
                                                               @NotNull Duration timeout) {
        final NetworkEnvelope envelope = new NetworkEnvelope(topic, origin, UUID.randomUUID().toString(), false, payload);
        return connection.requestWithTimeout(Subjects.bus(prefix, topic), codec.encode(envelope), timeout)
                .thenApply(message -> {
                    try {
                        return codec.decode(message.getData()).getPayload();
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                });
    }

    public void tell(@NotNull String topic, @NotNull Map<String, String> payload) {
        connection.publish(Subjects.bus(prefix, topic), codec.encode(new NetworkEnvelope(topic, origin, null, false, payload)));
    }
}
