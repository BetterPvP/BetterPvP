package me.mykindos.betterpvp.clans.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A bus message as it travels over NATS: JSON holding a topic and a flat map of strings, the same shape the plugin's
 * {@code BusMessage} carries. The NATS subject is the topic.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusEnvelope {

    private static final ObjectMapper JSON = new ObjectMapper();

    private String topic;
    private String origin;
    private String correlationId;
    private boolean reply;
    private Map<String, String> payload = new HashMap<>();

    public byte @NotNull [] encode() {
        try {
            return JSON.writeValueAsBytes(this);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("A map of strings always encodes", ex);
        }
    }

    public static @NotNull BusEnvelope decode(byte @NotNull [] data) throws IOException {
        return JSON.readValue(data, BusEnvelope.class);
    }
}
