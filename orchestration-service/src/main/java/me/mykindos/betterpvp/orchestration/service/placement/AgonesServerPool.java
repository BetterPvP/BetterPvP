package me.mykindos.betterpvp.orchestration.service.placement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.java.Log;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Game servers from Agones, read and allocated through the Kubernetes API.
 * <p>
 * A role maps to a fleet and a counter. Allocated servers of the fleet with room on the counter are the ones taking
 * sites, with {@code capacity - count} free slots. Another server is a {@code GameServerAllocation} against the fleet.
 */
@Log
public class AgonesServerPool implements ServerPool {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final String apiUrl;
    private final String namespace;
    private final Supplier<String> token;
    private final Map<String, Fleet> fleets;

    /**
     * @param token a bearer token for the API, or a supplier of {@code null} for none. Read on every call, since a
     *              projected service account token is rotated on disk.
     */
    public AgonesServerPool(HttpClient http, String apiUrl, String namespace, Supplier<String> token,
                            Map<String, Fleet> fleets) {
        this.http = http;
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.namespace = namespace;
        this.token = token;
        this.fleets = Map.copyOf(fleets);
    }

    @Override
    public List<Slots> servers(String role) throws IOException {
        final Fleet fleet = fleet(role);
        final String selector = URLEncoder.encode("agones.dev/fleet=" + fleet.getName(), StandardCharsets.UTF_8);
        final JsonNode list = send(request("/apis/agones.dev/v1/namespaces/" + namespace
                + "/gameservers?labelSelector=" + selector).GET().build());

        final List<Slots> servers = new ArrayList<>();
        for (JsonNode server : list.path("items")) {
            if (!"Allocated".equals(server.path("status").path("state").asText())) {
                continue;
            }
            final JsonNode counter = server.path("status").path("counters").path(fleet.getCounter());
            if (counter.isMissingNode()) {
                continue;
            }
            servers.add(new Slots(server.path("metadata").path("name").asText(),
                    counter.path("capacity").asInt() - counter.path("count").asInt()));
        }
        return servers;
    }

    @Override
    public Optional<String> allocate(String role) throws IOException {
        final Fleet fleet = fleet(role);
        final Map<String, Object> allocation = Map.of(
                "apiVersion", "allocation.agones.dev/v1",
                "kind", "GameServerAllocation",
                "spec", Map.of("selectors", List.of(Map.of(
                        "matchLabels", Map.of("agones.dev/fleet", fleet.getName()),
                        "gameServerState", "Ready"))));

        final JsonNode answer = send(request("/apis/allocation.agones.dev/v1/namespaces/" + namespace
                + "/gameserverallocations")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(allocation)))
                .build());

        final String state = answer.path("status").path("state").asText();
        if (!"Allocated".equals(state)) {
            if (fleet.isScaleFromZero()) {
                // Nothing is Ready, so start one. The ask after it boots gets it.
                scale(fleet, 1);
                log.info("Starting a " + role + " server in fleet " + fleet.getName());
            } else {
                log.warning("Agones could not allocate a " + role + " server from fleet " + fleet.getName() + ": " + state);
            }
            return Optional.empty();
        }

        if (fleet.isScaleFromZero()) {
            // Agones never removes an Allocated server when a fleet shrinks, so this one stays until it shuts down.
            scale(fleet, 0);
        }
        return Optional.of(answer.path("status").path("gameServerName").asText());
    }

    private void scale(Fleet fleet, int replicas) throws IOException {
        final Map<String, Object> patch = Map.of("spec", Map.of("replicas", replicas));
        send(request("/apis/agones.dev/v1/namespaces/" + namespace + "/fleets/" + fleet.getName())
                .header("Content-Type", "application/merge-patch+json")
                .method("PATCH", HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(patch)))
                .build());
    }

    private Fleet fleet(String role) throws IOException {
        final Fleet fleet = fleets.get(role);
        if (fleet == null) {
            throw new IOException("No fleet is configured for role " + role);
        }
        return fleet;
    }

    private HttpRequest.Builder request(String path) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl + path))
                .timeout(Duration.ofSeconds(10));
        final String bearer = token.get();
        if (bearer != null && !bearer.isBlank()) {
            builder.header("Authorization", "Bearer " + bearer.trim());
        }
        return builder;
    }

    private JsonNode send(HttpRequest request) throws IOException {
        try {
            final HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("Kubernetes answered " + response.statusCode() + " to " + request.uri()
                        + ": " + new String(response.body(), StandardCharsets.UTF_8));
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted calling " + request.uri(), interrupted);
        }
    }

    /**
     * The Agones fleet a role's servers come from, and the counter that holds their slots. A fleet that scales from
     * zero is started when a server is wanted and none is Ready, and sits at zero otherwise.
     */
    @Value
    @AllArgsConstructor
    public static class Fleet {
        String name;
        String counter;
        boolean scaleFromZero;

        public Fleet(String name, String counter) {
            this(name, counter, false);
        }
    }
}
