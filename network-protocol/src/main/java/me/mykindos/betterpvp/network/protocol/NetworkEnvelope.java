package me.mykindos.betterpvp.network.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * One bus message as it travels between servers and services, encoded as JSON by {@link EnvelopeCodec}.
 * <p>
 * Fields a reader does not know are ignored, so a newer sender can add one without breaking an older reader.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NetworkEnvelope {

    public static final int VERSION = 1;

    @JsonProperty("v")
    private int version = VERSION;

    private String topic;

    /** The server or service that sent it. */
    private String origin;

    /** Set on a request and copied onto every reply to it. Null on a plain publish. */
    private String correlationId;

    /** Whether this answers a request. Replies travel on the request's reply subject, never on a topic subject. */
    private boolean reply;

    private Map<String, String> payload = Map.of();

    public NetworkEnvelope(String topic, String origin, String correlationId, boolean reply,
                           Map<String, String> payload) {
        this(VERSION, topic, origin, correlationId, reply, payload);
    }
}
