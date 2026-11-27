package me.mykindos.betterpvp.ledger.service;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import me.mykindos.betterpvp.ledger.LedgerProtocol;
import me.mykindos.betterpvp.network.protocol.EnvelopeCodec;
import me.mykindos.betterpvp.network.protocol.NetworkEnvelope;
import me.mykindos.betterpvp.network.protocol.Subjects;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Takes ledger requests off the NATS bus subjects of the ledger topics and answers them on each request's reply
 * subject.
 * <p>
 * Every ledger instance joins one queue group, so each request reaches exactly one of them.
 */
public class NatsLedgerEndpoint implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(NatsLedgerEndpoint.class.getName());
    private static final String QUEUE_GROUP = "ledger";

    private final Connection connection;
    private final String prefix;
    private final LedgerRequests requests;
    private final ExecutorService workers;
    private final EnvelopeCodec codec = new EnvelopeCodec();
    private Dispatcher dispatcher;

    public NatsLedgerEndpoint(@NotNull Connection connection, @NotNull String prefix, @NotNull LedgerRequests requests,
                              @NotNull ExecutorService workers) {
        this.connection = connection;
        this.prefix = prefix;
        this.requests = requests;
        this.workers = workers;
    }

    public void start() {
        dispatcher = connection.createDispatcher(message -> workers.execute(() -> handle(message)));
        dispatcher.subscribe(Subjects.bus(prefix, LedgerProtocol.POST), QUEUE_GROUP);
        dispatcher.subscribe(Subjects.bus(prefix, LedgerProtocol.OPEN), QUEUE_GROUP);
    }

    private void handle(@NotNull Message message) {
        if (message.getReplyTo() == null) {
            return;
        }
        try {
            final NetworkEnvelope request = codec.decode(message.getData());
            final Map<String, String> answer = requests.answer(request.getTopic(), request.getPayload());
            final NetworkEnvelope reply = new NetworkEnvelope(request.getTopic(), QUEUE_GROUP,
                    request.getCorrelationId(), true, answer);
            connection.publish(message.getReplyTo(), codec.encode(reply));
        } catch (IOException ex) {
            LOG.log(System.Logger.Level.WARNING, "Dropped an unreadable request on " + message.getSubject(), ex);
        }
    }

    @Override
    public void close() {
        if (dispatcher != null) {
            connection.closeDispatcher(dispatcher);
        }
        workers.shutdown();
    }
}
