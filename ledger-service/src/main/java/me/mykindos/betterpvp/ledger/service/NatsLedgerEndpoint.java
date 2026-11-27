package me.mykindos.betterpvp.ledger.service;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import me.mykindos.betterpvp.ledger.LedgerEnvelope;
import me.mykindos.betterpvp.ledger.LedgerProtocol;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Takes ledger requests off NATS and answers them on each request's reply subject.
 * <p>
 * Every ledger instance joins one queue group, so each request reaches exactly one of them.
 */
public class NatsLedgerEndpoint implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(NatsLedgerEndpoint.class.getName());
    private static final String QUEUE_GROUP = "ledger";

    private final Connection connection;
    private final LedgerRequests requests;
    private final ExecutorService workers;
    private Dispatcher dispatcher;

    public NatsLedgerEndpoint(@NotNull Connection connection, @NotNull LedgerRequests requests,
                              @NotNull ExecutorService workers) {
        this.connection = connection;
        this.requests = requests;
        this.workers = workers;
    }

    public void start() {
        dispatcher = connection.createDispatcher(message -> workers.execute(() -> handle(message)));
        dispatcher.subscribe(LedgerProtocol.POST, QUEUE_GROUP);
        dispatcher.subscribe(LedgerProtocol.OPEN, QUEUE_GROUP);
    }

    private void handle(@NotNull Message message) {
        if (message.getReplyTo() == null) {
            return;
        }
        try {
            final LedgerEnvelope request = LedgerEnvelope.fromBytes(message.getData());
            final Map<String, String> answer = requests.answer(message.getSubject(), request.payloadOrEmpty());
            final LedgerEnvelope reply = new LedgerEnvelope(request.getTopic(), QUEUE_GROUP,
                    request.getCorrelationId(), true, answer);
            connection.publish(message.getReplyTo(), reply.toBytes());
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
