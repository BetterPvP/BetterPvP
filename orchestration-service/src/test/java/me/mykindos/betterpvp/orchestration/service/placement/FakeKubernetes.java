package me.mykindos.betterpvp.orchestration.service.placement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Agones calls Placement makes, answered from memory. Allocating turns the next Ready server into an
 * Allocated one, the way Agones does.
 */
class FakeKubernetes implements AutoCloseable {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpServer server;

    final List<Map<String, Object>> gameServers = new CopyOnWriteArrayList<>();
    final List<String> allocationBodies = new CopyOnWriteArrayList<>();
    final List<String> listQueries = new CopyOnWriteArrayList<>();
    final List<String> authorizations = new CopyOnWriteArrayList<>();
    /** Each fleet patch as {@code <fleet> <body>}. */
    final List<String> fleetPatches = new CopyOnWriteArrayList<>();

    FakeKubernetes() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/apis/agones.dev/v1/namespaces/games/gameservers", this::list);
        server.createContext("/apis/allocation.agones.dev/v1/namespaces/games/gameserverallocations", this::allocate);
        server.createContext("/apis/agones.dev/v1/namespaces/games/fleets/", this::patchFleet);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void addServer(String name, String state, int count, int capacity) {
        gameServers.add(Map.of(
                "metadata", Map.of("name", name, "labels", Map.of("agones.dev/fleet", "camp")),
                "status", Map.of("state", state,
                        "counters", Map.of("camp-slots", Map.of("count", count, "capacity", capacity)))));
    }

    private void list(HttpExchange exchange) throws IOException {
        listQueries.add(exchange.getRequestURI().getRawQuery());
        authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        respond(exchange, Map.of("items", gameServers));
    }

    private synchronized void allocate(HttpExchange exchange) throws IOException {
        allocationBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

        for (int i = 0; i < gameServers.size(); i++) {
            final Map<String, Object> candidate = gameServers.get(i);
            @SuppressWarnings("unchecked")
            final Map<String, Object> status = (Map<String, Object>) candidate.get("status");
            if ("Ready".equals(status.get("state"))) {
                @SuppressWarnings("unchecked")
                final String name = (String) ((Map<String, Object>) candidate.get("metadata")).get("name");
                gameServers.set(i, Map.of("metadata", candidate.get("metadata"),
                        "status", Map.of("state", "Allocated", "counters", status.get("counters"))));
                respond(exchange, Map.of("status", Map.of("state", "Allocated", "gameServerName", name)));
                return;
            }
        }
        respond(exchange, Map.of("status", Map.of("state", "UnAllocated")));
    }

    private void patchFleet(HttpExchange exchange) throws IOException {
        final String path = exchange.getRequestURI().getPath();
        fleetPatches.add(path.substring(path.lastIndexOf('/') + 1) + " "
                + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, Map.of());
    }

    private void respond(HttpExchange exchange, Object body) throws IOException {
        final byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
