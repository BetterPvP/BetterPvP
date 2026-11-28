package me.mykindos.betterpvp.orchestration.service.placement;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import lombok.extern.java.Log;
import me.mykindos.betterpvp.network.protocol.EnvelopeCodec;
import me.mykindos.betterpvp.network.protocol.NetworkEnvelope;
import me.mykindos.betterpvp.network.protocol.PlacementMessages;
import me.mykindos.betterpvp.network.protocol.Subjects;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Answers {@link PlacementMessages#PLACE} and {@link PlacementMessages#RELEASE} requests on the bus with what the
 * {@link Placer} decides.
 */
@Log
public class PlacementResponder implements AutoCloseable {

    private final EnvelopeCodec codec = new EnvelopeCodec();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Connection connection;
    private final Placer placer;
    private final String origin;
    private Dispatcher dispatcher;

    public PlacementResponder(Connection connection, Placer placer, String origin) {
        this.connection = connection;
        this.placer = placer;
        this.origin = origin;
    }

    /** Joins the {@code placement} queue group, so each request is answered by one replica only. */
    public void start(String prefix) {
        dispatcher = connection.createDispatcher(this::onMessage);
        dispatcher.subscribe(Subjects.bus(prefix, PlacementMessages.PLACE), "placement");
        dispatcher.subscribe(Subjects.bus(prefix, PlacementMessages.RELEASE), "placement");
    }

    @Override
    public void close() {
        if (dispatcher != null && connection.getStatus() != Connection.Status.CLOSED) {
            connection.closeDispatcher(dispatcher);
        }
        executor.shutdown();
    }

    private void onMessage(Message message) {
        final String replyTo = message.getReplyTo();
        if (replyTo == null) {
            return;
        }

        // Deciding can mean a round trip to Kubernetes, which must not hold up the next request.
        executor.execute(() -> {
            try {
                final NetworkEnvelope request = codec.decode(message.getData());
                if (request.isReply()) {
                    return;
                }
                connection.publish(replyTo, codec.encode(answer(request)));
            } catch (Exception exception) {
                log.log(Level.WARNING, "Could not answer a placement request on " + message.getSubject(), exception);
            }
        });
    }

    private NetworkEnvelope answer(NetworkEnvelope request) {
        final Map<String, String> asked = request.getPayload();
        final Map<String, String> reply = new HashMap<>();

        final String siteId = asked.get(PlacementMessages.SITE);
        if (siteId != null && PlacementMessages.RELEASE.equals(request.getTopic())) {
            placer.release(siteId, Long.parseLong(asked.getOrDefault(PlacementMessages.OWNER, "0")),
                    asked.getOrDefault(PlacementMessages.SERVER, ""));
            reply.put(PlacementMessages.OUTCOME, PlacementMessages.RELEASED);
        } else if (siteId == null) {
            reply.put(PlacementMessages.OUTCOME, PlacementMessages.UNAVAILABLE);
            reply.put(PlacementMessages.REASON, "no site was named");
        } else {
            final PlacementDecision decision = placer.place(siteId,
                    Long.parseLong(asked.getOrDefault(PlacementMessages.OWNER, "0")),
                    Boolean.parseBoolean(asked.get(PlacementMessages.OWNED)));
            reply.put(PlacementMessages.OUTCOME, decision.getOutcome());
            if (decision.getServer() != null) {
                reply.put(PlacementMessages.SERVER, decision.getServer());
            }
            if (decision.getReason() != null) {
                reply.put(PlacementMessages.REASON, decision.getReason());
            }
        }

        return new NetworkEnvelope(request.getTopic(), origin, request.getCorrelationId(), true, reply);
    }
}
