package me.mykindos.betterpvp.ledger;

import io.nats.client.Connection;
import io.nats.client.Message;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Asks the ledger service over NATS, on a subject named after the topic. */
public class NatsLedgerTransport implements LedgerTransport {

    private final Connection connection;
    private final String origin;
    private final Duration timeout;

    public NatsLedgerTransport(@NotNull Connection connection, @NotNull String origin, @NotNull Duration timeout) {
        this.connection = connection;
        this.origin = origin;
        this.timeout = timeout;
    }

    @Override
    public @NotNull CompletableFuture<LedgerReceipt> ask(@NotNull String topic, @NotNull Map<String, String> payload) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(LedgerReceipt.of(LedgerStatus.UNAVAILABLE));
        }
        final LedgerEnvelope request = new LedgerEnvelope(topic, origin, UUID.randomUUID().toString(), false, payload);
        try {
            return connection.requestWithTimeout(topic, request.toBytes(), timeout)
                    .thenApply(NatsLedgerTransport::receipt)
                    .exceptionally(ex -> LedgerReceipt.of(LedgerStatus.UNAVAILABLE));
        } catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(LedgerReceipt.of(LedgerStatus.UNAVAILABLE));
        }
    }

    @Override
    public boolean isAvailable() {
        return connection.getStatus() == Connection.Status.CONNECTED;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static @NotNull LedgerReceipt receipt(@NotNull Message message) {
        try {
            return LedgerProtocol.decodeReceipt(LedgerEnvelope.fromBytes(message.getData()).payloadOrEmpty());
        } catch (IOException | IllegalArgumentException ex) {
            return LedgerReceipt.of(LedgerStatus.UNAVAILABLE);
        }
    }
}
