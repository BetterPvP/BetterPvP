package me.mykindos.betterpvp.network.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The wire format servers and services share")
class WireFormatTest {

    private final EnvelopeCodec codec = new EnvelopeCodec();

    @Test
    @DisplayName("an envelope survives the round trip")
    void roundTrip() throws IOException {
        final NetworkEnvelope sent = new NetworkEnvelope("chat.private", "Clans-1", "abc", true, Map.of("to", "Tester"));

        final NetworkEnvelope read = codec.decode(codec.encode(sent));

        assertEquals(sent, read);
        assertEquals(NetworkEnvelope.VERSION, read.getVersion());
    }

    @Test
    @DisplayName("the JSON a service writes by hand is read, with absent fields defaulted and unknown ones ignored")
    void handWrittenJson() throws IOException {
        final String json = "{\"v\":1,\"topic\":\"ledger.balance\",\"origin\":\"ledger\",\"future\":42}";

        final NetworkEnvelope read = codec.decode(json.getBytes(StandardCharsets.UTF_8));

        assertEquals("ledger.balance", read.getTopic());
        assertEquals("ledger", read.getOrigin());
        assertNull(read.getCorrelationId());
        assertFalse(read.isReply());
        assertTrue(read.getPayload().isEmpty());
    }

    @Test
    @DisplayName("a plain publish leaves the correlation out of the JSON")
    void nullFieldsAreLeftOut() {
        final String json = new String(codec.encode(new NetworkEnvelope("chat.channel", "Clans-1", null, false,
                Map.of())), StandardCharsets.UTF_8);

        assertFalse(json.contains("correlationId"), json);
        assertTrue(json.contains("\"v\":1"), json);
    }

    @Test
    @DisplayName("an envelope without a topic is refused")
    void topicIsRequired() {
        assertThrows(IOException.class, () -> codec.decode("{\"v\":1}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("a topic becomes a subject under the bus prefix, and a topic NATS cannot carry is refused")
    void subjects() {
        assertEquals("bpvp.bus.chat.private", Subjects.bus(Subjects.DEFAULT_PREFIX, "chat.private"));
        assertEquals("bpvp.bus.>", Subjects.allBus(Subjects.DEFAULT_PREFIX));
        assertThrows(IllegalArgumentException.class, () -> Subjects.bus("bpvp", "chat.*"));
        assertThrows(IllegalArgumentException.class, () -> Subjects.bus("bpvp", "chat..private"));
        assertThrows(IllegalArgumentException.class, () -> Subjects.bus("bpvp", "chat private"));
    }

    @Test
    @DisplayName("directory keys escape anything a key token cannot hold")
    void directoryKeys() {
        final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertEquals("camp=3Anorth", DirectoryKeys.token("camp:north"));
        assertEquals("dungeon_1.42." + id, DirectoryKeys.instance("dungeon_1", 42, id));
        assertEquals("dungeon_1.42.*", DirectoryKeys.instancesOf("dungeon_1", 42));
        assertEquals("a=2Eb", DirectoryKeys.token("a.b"), "a dot would split the key into another token");
    }
}
