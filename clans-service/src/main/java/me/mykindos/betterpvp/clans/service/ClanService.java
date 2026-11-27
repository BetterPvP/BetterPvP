package me.mykindos.betterpvp.clans.service;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import me.mykindos.betterpvp.clans.protocol.ClanChange;
import me.mykindos.betterpvp.clans.protocol.ClanMutation;
import me.mykindos.betterpvp.clans.protocol.ClanReply;
import me.mykindos.betterpvp.clans.protocol.ClanTopics;
import me.mykindos.betterpvp.network.protocol.EnvelopeCodec;
import me.mykindos.betterpvp.network.protocol.NetworkEnvelope;
import me.mykindos.betterpvp.network.protocol.Subjects;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Answers {@link ClanTopics#MUTATE} over NATS and announces each applied mutation on {@link ClanTopics#CHANGED}, on
 * the same subjects and in the same envelope as the servers' message bus.
 * <p>
 * One NATS dispatcher thread handles every request, so mutations from every server are applied one after another in
 * the order NATS delivered them. Run a single replica: a second would apply in parallel and lose that order.
 */
public class ClanService {

    private static final System.Logger LOG = System.getLogger(ClanService.class.getName());

    private final Connection nats;
    private final ClanStore store;
    private final String prefix;
    private final EnvelopeCodec codec = new EnvelopeCodec();
    private final String origin = "clans-service-" + UUID.randomUUID();
    private Dispatcher dispatcher;
    private long sequence;

    /** @param prefix the subject prefix the servers use, which keeps networks sharing a NATS cluster apart */
    public ClanService(@NotNull Connection nats, @NotNull ClanStore store, @NotNull String prefix) {
        this.nats = nats;
        this.store = store;
        this.prefix = prefix;
    }

    public void start() {
        dispatcher = nats.createDispatcher(this::handle);
        dispatcher.subscribe(Subjects.bus(prefix, ClanTopics.MUTATE));
    }

    public void stop() {
        if (dispatcher != null) {
            nats.closeDispatcher(dispatcher);
        }
    }

    private void handle(@NotNull Message message) {
        final NetworkEnvelope request;
        try {
            request = codec.decode(message.getData());
        } catch (IOException ex) {
            LOG.log(System.Logger.Level.WARNING, "Dropped an unreadable clan mutation", ex);
            return;
        }
        if (request.isReply()) {
            return;
        }

        final ClanReply reply = apply(request);
        if (message.getReplyTo() != null) {
            final NetworkEnvelope answer = new NetworkEnvelope(ClanTopics.MUTATE, origin, request.getCorrelationId(),
                    true, reply.toPayload());
            nats.publish(message.getReplyTo(), codec.encode(answer));
        }
    }

    private @NotNull ClanReply apply(@NotNull NetworkEnvelope request) {
        final ClanMutation mutation;
        try {
            mutation = ClanMutation.fromPayload(request.getPayload());
        } catch (IllegalArgumentException | NullPointerException ex) {
            return ClanReply.refused("Malformed mutation: " + ex.getMessage());
        }

        try {
            store.apply(mutation);
        } catch (ClanRefusal refusal) {
            return ClanReply.refused(refusal.getMessage());
        } catch (SQLException | RuntimeException ex) {
            LOG.log(System.Logger.Level.ERROR, "Failed to apply " + mutation.getOp() + " to clan " + mutation.getClan(), ex);
            return ClanReply.refused("The clan store failed");
        }

        final long applied = ++sequence;
        final List<Long> clans = mutation.getOther() != null
                ? List.of(mutation.getClan(), mutation.getOther())
                : List.of(mutation.getClan());
        final ClanChange change = new ClanChange(applied, mutation.getOp(), clans);
        final NetworkEnvelope announcement = new NetworkEnvelope(ClanTopics.CHANGED, origin, null, false,
                change.toPayload());
        nats.publish(Subjects.bus(prefix, ClanTopics.CHANGED), codec.encode(announcement));
        return ClanReply.applied(applied);
    }
}
