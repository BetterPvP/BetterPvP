package me.mykindos.betterpvp.core.framework.net;

import io.nats.client.Connection;
import lombok.Value;
import me.mykindos.betterpvp.network.protocol.NetworkEnvelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("Talking to the other servers through NATS")
class NatsMessageBusTest extends MessageBusContract {

    /** One envelope the bus put on the wire, with the subjects it went on. */
    @Value
    private static class Published {
        String subject;
        String replyTo;
        NetworkEnvelope envelope;
    }

    private final List<Runnable> scheduled = new ArrayList<>();
    private final List<Published> published = new ArrayList<>();

    private NatsMessageBus bus;

    /** The bus with the network taken out, so the contract runs against its routing rather than against a server. */
    private class CapturingBus extends NatsMessageBus {
        CapturingBus() {
            super(mock(Connection.class), "bpvp", () -> "Clans-1");
        }

        @Override
        protected void send(String subject, String replyTo, NetworkEnvelope envelope) {
            published.add(new Published(subject, replyTo, envelope));
        }

        @Override
        protected void schedule(Runnable task, Duration delay) {
            scheduled.add(task);
        }
    }

    @BeforeEach
    void setUp() {
        bus = new CapturingBus();
    }

    @AfterEach
    void tearDown() {
        bus.close();
    }

    @Override
    protected MessageBus bus() {
        return bus;
    }

    @Override
    protected void arrive(String topic, String origin, String correlationId, boolean reply,
                          Map<String, String> payload) {
        final NetworkEnvelope envelope = new NetworkEnvelope(topic, origin, correlationId, reply, payload);
        if (reply) {
            bus.deliver(bus.inbox() + "." + correlationId, null, envelope);
        } else {
            // A request is a message with somewhere to send the answer, which is what NATS gives one.
            bus.deliver("bpvp.bus." + topic, correlationId == null ? null : "_INBOX.asker." + correlationId, envelope);
        }
    }

    @Override
    protected List<Sent> sent() {
        return published.stream()
                .map(sent -> new Sent(sent.getEnvelope().getTopic(), sent.getEnvelope().getCorrelationId(),
                        sent.getEnvelope().isReply(), sent.getEnvelope().getPayload()))
                .toList();
    }

    @Override
    protected void elapse() {
        final List<Runnable> due = List.copyOf(scheduled);
        scheduled.clear();
        due.forEach(Runnable::run);
    }

    @Test
    @DisplayName("a topic travels on its own subject, and a plain publish asks for no answer")
    void publishUsesTheTopicSubject() {
        bus.publish(BusMessage.of("chat.clan", Map.of("text", "hello")));

        assertEquals("bpvp.bus.chat.clan", published.get(0).getSubject());
        assertNull(published.get(0).getReplyTo());
        assertEquals("Clans-1", published.get(0).getEnvelope().getOrigin());
    }

    @Test
    @DisplayName("a request names a reply subject under this bus's inbox")
    void requestCarriesAReplySubject() {
        bus.request(BusMessage.of("player.find", "name", "Tester"), Duration.ofSeconds(1));

        final Published request = published.get(0);
        assertEquals(bus.inbox() + "." + request.getEnvelope().getCorrelationId(), request.getReplyTo());
    }

    @Test
    @DisplayName("an answer goes to the subject the asker gave, not back onto the topic")
    void answersGoToTheReplySubject() {
        bus.answer("player.find", request -> Optional.of(request.replyWith(Map.of("server", "Clans-1"))));

        bus.deliver("bpvp.bus.player.find", "_INBOX.someone.42", new NetworkEnvelope("player.find", "ledger", null,
                false, Map.of()));

        assertEquals("_INBOX.someone.42", published.get(0).getSubject());
        assertTrue(published.get(0).getEnvelope().isReply());
    }

    @Test
    @DisplayName("a reply that left the correlation out still reaches its request, since the subject names it")
    void replyMatchedBySubject() {
        final var answered = bus.request(BusMessage.of("player.find", "name", "Tester"), Duration.ofSeconds(1));
        final String replyTo = published.get(0).getReplyTo();

        bus.deliver(replyTo, null, new NetworkEnvelope("player.find", "sessions", null, true, Map.of("server", "Hub-1")));
        elapse();

        assertEquals(1, answered.join().size());
        assertEquals("sessions", answered.join().get(0).getOrigin());
    }

    @Test
    @DisplayName("asking one service ends at its reply, without waiting out the timeout")
    void askEndsAtTheFirstReply() {
        final var answered = bus.ask(BusMessage.of("placement.place", "site", "camp"), Duration.ofSeconds(5));
        final String replyTo = published.get(0).getReplyTo();

        bus.deliver(replyTo, null, new NetworkEnvelope("placement.place", "placement", null, true, Map.of("server", "Camp-1")));

        assertEquals(Optional.of("Camp-1"), answered.join().flatMap(reply -> reply.get("server")));
    }

    @Test
    @DisplayName("asking with no reply is empty once the timeout passes")
    void askWithoutReplyIsEmpty() {
        final var answered = bus.ask(BusMessage.of("placement.place", "site", "camp"), Duration.ofSeconds(5));
        elapse();

        assertTrue(answered.join().isEmpty());
    }
}
