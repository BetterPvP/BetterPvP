package me.mykindos.betterpvp.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

/**
 * A ledger request or reply as it travels: JSON on a NATS subject named after its topic, with the payload a flat
 * string map.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEnvelope {

    private static final ObjectMapper JSON = new ObjectMapper();

    private String topic;
    private String origin;
    private String correlationId;
    private boolean reply;
    private Map<String, String> payload;

    public @NotNull Map<String, String> payloadOrEmpty() {
        return payload == null ? Map.of() : payload;
    }

    public byte @NotNull [] toBytes() {
        try {
            return JSON.writeValueAsBytes(this);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** @throws IOException if the bytes are not an envelope */
    public static @NotNull LedgerEnvelope fromBytes(byte @NotNull [] bytes) throws IOException {
        return JSON.readValue(bytes, LedgerEnvelope.class);
    }
}
