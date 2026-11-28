package me.mykindos.betterpvp.proxy.network;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import me.mykindos.betterpvp.network.protocol.EnvelopeCodec;
import me.mykindos.betterpvp.network.protocol.NetworkEnvelope;
import me.mykindos.betterpvp.network.protocol.Subjects;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/** This proxy's side of the bus, in the format of {@code network-protocol}, signed with the proxy's name. */
public class ProxyBus implements AutoCloseable {

    private final EnvelopeCodec codec = new EnvelopeCodec();
    private final Connection connection;
    private final String prefix;
    private final String origin;
    private final Logger logger;
    private final Dispatcher dispatcher;

    public ProxyBus(Connection connection, String prefix, String origin, Logger logger) {
        this.connection = connection;
        this.prefix = prefix;
        this.origin = origin;
        this.logger = logger;
        // Every subscription brings its own handler, so the dispatcher's default one is never called.
        this.dispatcher = connection.createDispatcher(message -> {
        });
    }

    public String origin() {
        return origin;
    }

    public boolean isConnected() {
        return connection.getStatus() == Connection.Status.CONNECTED;
    }

    public void publish(String topic, Map<String, String> payload) {
        connection.publish(Subjects.bus(prefix, topic), codec.encode(new NetworkEnvelope(topic, origin, null, false, payload)));
    }

    /** Completes with the first answer's payload, or exceptionally when nobody answered within {@code timeout}. */
    public CompletableFuture<Map<String, String>> ask(String topic, Map<String, String> payload, Duration timeout) {
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

    /** Hands every plain message on a topic to {@code handler} as its topic and payload. */
    public void subscribe(String topic, BiConsumer<String, Map<String, String>> handler) {
        dispatcher.subscribe(Subjects.bus(prefix, topic), message -> {
            try {
                final NetworkEnvelope envelope = codec.decode(message.getData());
                if (!envelope.isReply()) {
                    handler.accept(envelope.getTopic(), envelope.getPayload());
                }
            } catch (Exception exception) {
                logger.warn("Could not read a message on {}", message.getSubject(), exception);
            }
        });
    }

    /** Waits until everything published so far has reached NATS. */
    public void flush(Duration timeout) {
        try {
            connection.flush(timeout);
        } catch (Exception exception) {
            logger.warn("Could not flush to NATS", exception);
        }
    }

    @Override
    public void close() {
        if (connection.getStatus() != Connection.Status.CLOSED) {
            connection.closeDispatcher(dispatcher);
        }
    }
}
