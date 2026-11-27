package me.mykindos.betterpvp.sessions.service;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import me.mykindos.betterpvp.network.protocol.EnvelopeCodec;
import me.mykindos.betterpvp.network.protocol.NetworkEnvelope;
import me.mykindos.betterpvp.network.protocol.Subjects;
import me.mykindos.betterpvp.sessions.SessionProtocol;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Takes session requests off NATS and answers them on each request's reply subject.
 * <p>
 * A claim on a player another server owns is retried until that lease is released or expires, or the claim's wait
 * runs out, and only then answered.
 */
public class SessionEndpoint implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(SessionEndpoint.class.getName());
    private static final String ORIGIN = "sessions";

    private final Connection connection;
    private final String prefix;
    private final LeaseBook book;
    private final ScheduledExecutorService workers;
    private final long maxWaitMillis;
    private final EnvelopeCodec codec = new EnvelopeCodec();
    private Dispatcher dispatcher;

    public SessionEndpoint(@NotNull Connection connection, @NotNull String prefix, @NotNull LeaseBook book,
                           @NotNull ScheduledExecutorService workers, long maxWaitMillis) {
        this.connection = connection;
        this.prefix = prefix;
        this.book = book;
        this.workers = workers;
        this.maxWaitMillis = maxWaitMillis;
    }

    public void start() {
        dispatcher = connection.createDispatcher(message -> workers.execute(() -> handle(message)));
        // One queue group, so each request reaches one instance however many run
        dispatcher.subscribe(Subjects.bus(prefix, SessionProtocol.CLAIM), ORIGIN);
        dispatcher.subscribe(Subjects.bus(prefix, SessionProtocol.RELEASE), ORIGIN);
        dispatcher.subscribe(Subjects.bus(prefix, SessionProtocol.HEARTBEAT), ORIGIN);
        dispatcher.subscribe(Subjects.bus(prefix, SessionProtocol.LOGIN), ORIGIN);
        dispatcher.subscribe(Subjects.bus(prefix, SessionProtocol.LOGOUT), ORIGIN);
        dispatcher.subscribe(Subjects.bus(prefix, SessionProtocol.PROXY_HEARTBEAT), ORIGIN);
    }

    private void handle(@NotNull Message message) {
        final NetworkEnvelope request;
        try {
            request = codec.decode(message.getData());
        } catch (IOException exception) {
            LOG.log(System.Logger.Level.WARNING, "Dropped an unreadable request on " + message.getSubject(), exception);
            return;
        }

        final Map<String, String> payload = request.getPayload();
        try {
            switch (request.getTopic()) {
                case SessionProtocol.CLAIM -> {
                    final long wait = Math.min(maxWaitMillis, Long.parseLong(payload.getOrDefault(SessionProtocol.WAIT, "0")));
                    claim(message, request, System.currentTimeMillis() + wait);
                }
                case SessionProtocol.RELEASE -> {
                    final boolean released = book.release(player(payload), payload.get(SessionProtocol.SERVER),
                            Long.parseLong(payload.get(SessionProtocol.EPOCH)));
                    reply(message, request, Map.of(SessionProtocol.RESULT,
                            released ? SessionProtocol.RELEASED : SessionProtocol.STALE));
                }
                case SessionProtocol.HEARTBEAT -> book.heartbeat(payload.get(SessionProtocol.SERVER));
                case SessionProtocol.LOGIN -> {
                    final boolean accepted = book.login(player(payload), payload.get(SessionProtocol.PROXY));
                    reply(message, request, Map.of(SessionProtocol.RESULT,
                            accepted ? SessionProtocol.ACCEPTED : SessionProtocol.REFUSED));
                }
                case SessionProtocol.LOGOUT -> {
                    book.logout(player(payload), payload.get(SessionProtocol.PROXY));
                    reply(message, request, Map.of(SessionProtocol.RESULT, SessionProtocol.RELEASED));
                }
                case SessionProtocol.PROXY_HEARTBEAT -> book.proxyHeartbeat(payload.get(SessionProtocol.PROXY));
                default -> LOG.log(System.Logger.Level.WARNING, "Ignored a request on unknown topic " + request.getTopic());
            }
        } catch (RuntimeException exception) {
            LOG.log(System.Logger.Level.WARNING, "Dropped a malformed " + request.getTopic() + " request", exception);
        }
    }

    private void claim(@NotNull Message message, @NotNull NetworkEnvelope request, long deadline) {
        final Map<String, String> payload = request.getPayload();
        final Claim claim = book.claim(player(payload), payload.get(SessionProtocol.SERVER));
        if (claim.isGranted()) {
            reply(message, request, Map.of(SessionProtocol.RESULT, SessionProtocol.GRANTED,
                    SessionProtocol.EPOCH, String.valueOf(claim.getEpoch())));
        } else if (System.currentTimeMillis() >= deadline) {
            reply(message, request, Map.of(SessionProtocol.RESULT, SessionProtocol.HELD,
                    SessionProtocol.HOLDER, claim.getHolder()));
        } else {
            workers.schedule(() -> claim(message, request, deadline), 100, TimeUnit.MILLISECONDS);
        }
    }

    private void reply(@NotNull Message message, @NotNull NetworkEnvelope request, @NotNull Map<String, String> answer) {
        if (message.getReplyTo() == null) {
            return;
        }
        connection.publish(message.getReplyTo(),
                codec.encode(new NetworkEnvelope(request.getTopic(), ORIGIN, request.getCorrelationId(), true, answer)));
    }

    private static UUID player(@NotNull Map<String, String> payload) {
        return UUID.fromString(payload.get(SessionProtocol.PLAYER));
    }

    @Override
    public void close() {
        if (dispatcher != null) {
            connection.closeDispatcher(dispatcher);
        }
        workers.shutdown();
    }
}
