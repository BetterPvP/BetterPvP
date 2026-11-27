package me.mykindos.betterpvp.ledger;

import io.nats.client.Connection;
import io.nats.client.Message;
import me.mykindos.betterpvp.network.protocol.EnvelopeCodec;
import me.mykindos.betterpvp.network.protocol.NetworkEnvelope;
import me.mykindos.betterpvp.network.protocol.Subjects;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Asks the ledger service over NATS, on the bus subject of the topic, and takes the first reply. The connection
 * belongs to whoever made it.
 */
public class NatsLedgerTransport implements LedgerTransport {

    private final Connection connection;
    private final String prefix;
    private final String origin;
    private final Duration timeout;
    private final EnvelopeCodec codec = new EnvelopeCodec();

    public NatsLedgerTransport(@NotNull Connection connection, @NotNull String prefix, @NotNull String origin,
                               @NotNull Duration timeout) {
        this.connection = connection;
        this.prefix = prefix;
        this.origin = origin;
        this.timeout = timeout;
    }

    @Override
    public @NotNull CompletableFuture<LedgerReceipt> ask(@NotNull String topic, @NotNull Map<String, String> payload) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(LedgerReceipt.of(LedgerStatus.UNAVAILABLE));
        }
        final NetworkEnvelope request = new NetworkEnvelope(topic, origin, UUID.randomUUID().toString(), false, payload);
        try {
            return connection.requestWithTimeout(Subjects.bus(prefix, topic), codec.encode(request), timeout)
                    .thenApply(this::receipt)
                    .exceptionally(ex -> LedgerReceipt.of(LedgerStatus.UNAVAILABLE));
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(LedgerReceipt.of(LedgerStatus.UNAVAILABLE));
        }
    }

    @Override
    public boolean isAvailable() {
        return connection.getStatus() == Connection.Status.CONNECTED;
    }

    private @NotNull LedgerReceipt receipt(@NotNull Message message) {
        try {
            return LedgerProtocol.decodeReceipt(codec.decode(message.getData()).getPayload());
        } catch (IOException | IllegalArgumentException ex) {
            return LedgerReceipt.of(LedgerStatus.UNAVAILABLE);
        }
    }
}
