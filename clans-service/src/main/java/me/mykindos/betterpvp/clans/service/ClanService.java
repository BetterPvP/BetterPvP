package me.mykindos.betterpvp.clans.service;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import me.mykindos.betterpvp.clans.protocol.ClanChange;
import me.mykindos.betterpvp.clans.protocol.ClanMutation;
import me.mykindos.betterpvp.clans.protocol.ClanOp;
import me.mykindos.betterpvp.clans.protocol.ClanReply;
import me.mykindos.betterpvp.clans.protocol.ClanTopics;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Answers {@link ClanTopics#MUTATE} over NATS and announces each applied mutation on {@link ClanTopics#CHANGED}.
 * <p>
 * One NATS dispatcher thread handles every request, so mutations from every server are applied one after another in
 * the order NATS delivered them. Run a single replica: a second would apply in parallel and lose that order.
 */
public class ClanService {

    private static final System.Logger LOG = System.getLogger(ClanService.class.getName());
    private static final Set<ClanOp> RELATIONS = Set.of(ClanOp.ALLY, ClanOp.UNALLY, ClanOp.TRUST, ClanOp.ENEMY, ClanOp.UNENEMY);

    private final Connection nats;
    private final ClanStore store;
    private final String origin = "clans-service-" + UUID.randomUUID();
    private Dispatcher dispatcher;
    private long sequence;

    public ClanService(@NotNull Connection nats, @NotNull ClanStore store) {
        this.nats = nats;
        this.store = store;
    }

    public void start() {
        dispatcher = nats.createDispatcher(this::handle);
        dispatcher.subscribe(ClanTopics.MUTATE);
    }

    public void stop() {
        if (dispatcher != null) {
            nats.closeDispatcher(dispatcher);
        }
    }

    private void handle(@NotNull Message message) {
        final BusEnvelope request;
        try {
            request = BusEnvelope.decode(message.getData());
        } catch (IOException ex) {
            LOG.log(System.Logger.Level.WARNING, "Dropped an unreadable clan mutation", ex);
            return;
        }

        final ClanReply reply = apply(request);
        if (message.getReplyTo() != null) {
            final BusEnvelope answer = new BusEnvelope(ClanTopics.MUTATE, origin, request.getCorrelationId(), true,
                    reply.toPayload());
            nats.publish(message.getReplyTo(), answer.encode());
        }
    }

    private @NotNull ClanReply apply(@NotNull BusEnvelope request) {
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
        final List<Long> clans = RELATIONS.contains(mutation.getOp()) && mutation.getOther() != null
                ? List.of(mutation.getClan(), mutation.getOther())
                : List.of(mutation.getClan());
        final ClanChange change = new ClanChange(applied, mutation.getOp(), clans);
        final BusEnvelope announcement = new BusEnvelope(ClanTopics.CHANGED, origin, null, false, change.toPayload());
        nats.publish(ClanTopics.CHANGED, announcement.encode());
        return ClanReply.applied(applied);
    }
}
