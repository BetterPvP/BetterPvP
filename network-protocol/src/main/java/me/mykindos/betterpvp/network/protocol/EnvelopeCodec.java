package me.mykindos.betterpvp.network.protocol;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

/** Turns envelopes and directory records into the JSON bytes that go on NATS, and back. Thread-safe. */
public class EnvelopeCodec {

    private final ObjectMapper mapper = new ObjectMapper();

    public byte[] encode(NetworkEnvelope envelope) {
        try {
            return mapper.writeValueAsBytes(envelope);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not encode a message on topic " + envelope.getTopic(), exception);
        }
    }

    /** Reads an envelope. A missing payload reads as an empty one. */
    public NetworkEnvelope decode(byte[] body) throws IOException {
        final NetworkEnvelope envelope = mapper.readValue(body, NetworkEnvelope.class);
        if (envelope.getTopic() == null) {
            throw new IOException("An envelope arrived without a topic");
        }
        if (envelope.getPayload() == null) {
            envelope.setPayload(Map.of());
        }
        return envelope;
    }

    /** Encodes a directory record, which is a flat JSON object of strings. */
    public byte[] encodeFields(Map<String, String> fields) {
        try {
            return mapper.writeValueAsBytes(fields);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not encode a directory record", exception);
        }
    }

    public Map<String, String> decodeFields(byte[] body) throws IOException {
        return mapper.readValue(body, new TypeReference<>() {
        });
    }
}
