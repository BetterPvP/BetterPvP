package me.mykindos.betterpvp.core.framework.net;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.NUID;
import lombok.CustomLog;
import me.mykindos.betterpvp.network.protocol.EnvelopeCodec;
import me.mykindos.betterpvp.network.protocol.NetworkEnvelope;
import me.mykindos.betterpvp.network.protocol.Subjects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link MessageBus} over NATS core subjects, in the format of {@code network-protocol}.
 * <p>
 * Each topic is its own subject, {@code <prefix>.bus.<topic>}, so a service can listen to exactly the topics it
 * serves. A request goes out with a reply subject under this bus's inbox, {@code _INBOX.<id>.<correlation>}, and
 * every server or service that answers publishes its reply there. One inbox subscription collects all of them.
 * <p>
 * NATS delivers a publish to this connection's own subscriptions too, so a publish reaches the local handlers once.
 */
@CustomLog
public class NatsMessageBus implements MessageBus {

    private final EnvelopeCodec codec = new EnvelopeCodec();

    private final Map<String, List<Consumer<BusMessage>>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<Function<BusMessage, Optional<BusMessage>>>> responders = new ConcurrentHashMap<>();
    private final Map<String, List<BusMessage>> awaitedReplies = new ConcurrentHashMap<>();
    private final Set<String> topics = ConcurrentHashMap.newKeySet();

    private final Connection connection;
    private final String prefix;
    private final Supplier<String> origin;
    private final String inbox = "_INBOX." + NUID.nextGlobal();

    private volatile boolean closed;
    private Dispatcher dispatcher;

    /**
     * @param origin the name this server signs its messages with, read on every send
     */
    public NatsMessageBus(@NotNull Connection connection, @NotNull String prefix, @NotNull Supplier<String> origin) {
        this.connection = connection;
        this.prefix = prefix;
        this.origin = origin;
    }

    /** Opens the subscriptions. Topics registered before this are subscribed here. */
    public synchronized void start() {
        dispatcher = connection.createDispatcher(this::onMessage);
        dispatcher.subscribe(inbox + ".*");
        topics.forEach(topic -> dispatcher.subscribe(Subjects.bus(prefix, topic)));
    }

    @Override
    public void publish(@NotNull BusMessage message) {
        send(Subjects.bus(prefix, message.getTopic()), null, envelope(message, false));
    }

    @Override
    public @NotNull CompletableFuture<List<BusMessage>> request(@NotNull BusMessage message,
                                                                @NotNull Duration timeout) {
        final String correlationId = UUID.randomUUID().toString();
        final List<BusMessage> replies = new CopyOnWriteArrayList<>();
        awaitedReplies.put(correlationId, replies);

        final CompletableFuture<List<BusMessage>> answered = new CompletableFuture<>();
        schedule(() -> {
            awaitedReplies.remove(correlationId);
            answered.complete(List.copyOf(replies));
        }, timeout);

        send(Subjects.bus(prefix, message.getTopic()), inbox + "." + correlationId, envelope(BusMessage.builder()
                .topic(message.getTopic())
                .correlationId(correlationId)
                .payload(message.getPayload())
                .build(), false));

        return answered;
    }

    @Override
    public void subscribe(@NotNull String topic, @NotNull Consumer<BusMessage> handler) {
        subscribers.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(handler);
        listen(topic);
    }

    @Override
    public void answer(@NotNull String topic, @NotNull Function<BusMessage, Optional<BusMessage>> responder) {
        responders.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(responder);
        listen(topic);
    }

    @Override
    public boolean isAvailable() {
        return !closed && connection.getStatus() == Connection.Status.CONNECTED;
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (dispatcher != null && connection.getStatus() != Connection.Status.CLOSED) {
            connection.closeDispatcher(dispatcher);
        }
        // The connection is shared with the directory, and closed by the NatsConnection that owns it.
    }

    /** Puts one envelope on the wire. The client buffers it, so this never waits on the network. */
    protected void send(@NotNull String subject, @Nullable String replyTo, @NotNull NetworkEnvelope envelope) {
        if (closed) {
            return;
        }

        try {
            connection.publish(subject, replyTo, codec.encode(envelope));
        } catch (IllegalStateException exception) {
            log.error("Failed to publish on topic '{}'", envelope.getTopic(), exception).submit();
        }
    }

    /** Completes a request once its timeout passes. */
    protected void schedule(@NotNull Runnable task, @NotNull Duration delay) {
        CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS).execute(task);
    }

    /** The subject prefix replies to this bus's requests arrive under. */
    @NotNull String inbox() {
        return inbox;
    }

    private synchronized void listen(@NotNull String topic) {
        if (topics.add(topic) && dispatcher != null) {
            dispatcher.subscribe(Subjects.bus(prefix, topic));
        }
    }

    private void onMessage(@NotNull Message message) {
        try {
            deliver(message.getSubject(), message.getReplyTo(), codec.decode(message.getData()));
        } catch (Exception exception) {
            log.error("Could not read a message off subject '{}'", message.getSubject(), exception).submit();
        }
    }

    /** Routes one arrival to whatever registered for it. Package-private so the routing can be tested. */
    void deliver(@NotNull String subject, @Nullable String replyTo, @NotNull NetworkEnvelope envelope) {
        final String inboxRoot = inbox + ".";
        if (subject.startsWith(inboxRoot)) {
            // The subject says which request this answers, so a service that never set the correlation still lands.
            final String correlationId = subject.substring(inboxRoot.length());
            final List<BusMessage> waiting = awaitedReplies.get(correlationId);
            if (waiting != null) {
                waiting.add(message(envelope, correlationId));
            }
            return;
        }

        if (envelope.isReply()) {
            return;
        }

        final BusMessage message = message(envelope, envelope.getCorrelationId());
        for (Consumer<BusMessage> handler : subscribers.getOrDefault(message.getTopic(), List.of())) {
            try {
                handler.accept(message);
            } catch (RuntimeException exception) {
                log.error("Handler for topic '{}' failed", message.getTopic(), exception).submit();
            }
        }

        if (replyTo == null) {
            return;
        }

        for (Function<BusMessage, Optional<BusMessage>> responder : responders.getOrDefault(message.getTopic(), List.of())) {
            try {
                responder.apply(message).ifPresent(reply -> send(replyTo, null, envelope(reply, true)));
            } catch (RuntimeException exception) {
                log.error("Responder for topic '{}' failed", message.getTopic(), exception).submit();
            }
        }
    }

    private @NotNull BusMessage message(@NotNull NetworkEnvelope envelope, @Nullable String correlationId) {
        return BusMessage.builder()
                .topic(envelope.getTopic())
                .origin(envelope.getOrigin())
                .correlationId(correlationId)
                .payload(Map.copyOf(envelope.getPayload()))
                .build();
    }

    private @NotNull NetworkEnvelope envelope(@NotNull BusMessage message, boolean reply) {
        final Map<String, String> payload = new HashMap<>();
        message.getPayload().forEach((key, value) -> {
            if (!key.startsWith(BusMessage.RESERVED_PREFIX)) {
                payload.put(key, value);
            }
        });

        return new NetworkEnvelope(message.getTopic(), origin.get(), message.getCorrelationId(), reply, payload);
    }
}
