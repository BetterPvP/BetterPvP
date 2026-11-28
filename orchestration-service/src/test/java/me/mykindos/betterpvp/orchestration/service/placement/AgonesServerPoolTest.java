package me.mykindos.betterpvp.orchestration.service.placement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Reading and allocating camp servers through Agones")
class AgonesServerPoolTest {

    private FakeKubernetes kubernetes;
    private AgonesServerPool pool;

    @BeforeEach
    void setUp() throws IOException {
        kubernetes = new FakeKubernetes();
        pool = new AgonesServerPool(HttpClient.newHttpClient(), kubernetes.url(), "games", () -> "secret-token",
                Map.of("camp", new AgonesServerPool.Fleet("camp", "camp-slots")));
    }

    @AfterEach
    void tearDown() {
        kubernetes.close();
    }

    @Test
    @DisplayName("allocated servers report their free slots from the counter, and others are left out")
    void readsCounters() throws IOException {
        kubernetes.addServer("camp-a", "Allocated", 3, 10);
        kubernetes.addServer("camp-b", "Allocated", 10, 10);
        kubernetes.addServer("camp-c", "Ready", 0, 10);
        kubernetes.addServer("camp-d", "Shutdown", 0, 10);

        final List<ServerPool.Slots> servers = pool.servers("camp");

        assertEquals(List.of(new ServerPool.Slots("camp-a", 7), new ServerPool.Slots("camp-b", 0)), servers);
        assertEquals("labelSelector=agones.dev%2Ffleet%3Dcamp", kubernetes.listQueries.get(0));
        assertEquals("Bearer secret-token", kubernetes.authorizations.get(0));
    }

    @Test
    @DisplayName("an allocation asks the role's fleet for a Ready server and answers its name")
    void allocates() throws IOException {
        kubernetes.addServer("camp-c", "Ready", 0, 10);

        assertEquals(Optional.of("camp-c"), pool.allocate("camp"));
        final String body = kubernetes.allocationBodies.get(0);
        assertTrue(body.contains("\"kind\":\"GameServerAllocation\""), body);
        assertTrue(body.contains("\"agones.dev/fleet\":\"camp\""), body);
        assertTrue(body.contains("\"gameServerState\":\"Ready\""), body);
    }

    @Test
    @DisplayName("an allocation Agones cannot fill is empty")
    void allocationCanFail() throws IOException {
        assertTrue(pool.allocate("camp").isEmpty());
    }

    @Test
    @DisplayName("a fleet that scales from zero starts a server when none is Ready, and goes back to zero once it is taken")
    void scalesFromZero() throws IOException {
        final AgonesServerPool events = new AgonesServerPool(HttpClient.newHttpClient(), kubernetes.url(), "games",
                () -> "secret-token", Map.of("event", new AgonesServerPool.Fleet("event", "players", true)));

        assertTrue(events.allocate("event").isEmpty());
        assertEquals(List.of("event {\"spec\":{\"replicas\":1}}"), kubernetes.fleetPatches);

        kubernetes.addServer("event-a", "Ready", 0, 200);
        assertEquals(Optional.of("event-a"), events.allocate("event"));
        assertEquals(List.of("event {\"spec\":{\"replicas\":1}}", "event {\"spec\":{\"replicas\":0}}"),
                kubernetes.fleetPatches);
    }

    @Test
    @DisplayName("an ordinary fleet is never scaled by Placement")
    void ordinaryFleetsAreLeftAlone() throws IOException {
        pool.allocate("camp");
        kubernetes.addServer("camp-c", "Ready", 0, 10);
        pool.allocate("camp");
        assertTrue(kubernetes.fleetPatches.isEmpty());
    }

    @Test
    @DisplayName("a role with no fleet configured is an error, not an empty pool")
    void unknownRole() {
        assertThrows(IOException.class, () -> pool.servers("dungeon"));
    }
}
