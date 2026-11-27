package me.mykindos.betterpvp.core.framework.agones;

import me.mykindos.betterpvp.core.framework.role.RoleGate;
import me.mykindos.betterpvp.core.framework.role.ServerRole;
import org.bukkit.Bukkit;
import org.bukkit.event.server.ServerLoadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

@DisplayName("Reporting the server lifecycle to Agones")
class AgonesLifecycleTest {

    private FakeSidecar sidecar;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() throws Exception {
        sidecar = new FakeSidecar();
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
        bukkit.when(Bukkit::getMaxPlayers).thenReturn(100);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
        sidecar.close();
    }

    @Test
    @DisplayName("The server goes Ready only once every plugin has loaded")
    void readyOnlyAfterPluginsLoad() throws Exception {
        final AgonesLifecycle lifecycle = new AgonesLifecycle(AgonesSdk.at(sidecar.base()),
                new RoleGate(ServerRole.RESOURCE));

        lifecycle.heartbeat();
        lifecycle.onServerLoad(new ServerLoadEvent(ServerLoadEvent.LoadType.RELOAD));
        assertTrue(sidecar.callsWithin(300).isEmpty());

        lifecycle.onServerLoad(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));
        final List<String> calls = sidecar.callsWithin(1000);
        assertTrue(calls.contains("POST /ready {}"), calls.toString());
        assertTrue(calls.contains("PUT /metadata/label {\"key\":\"role\",\"value\":\"resource\"}"), calls.toString());
        assertTrue(calls.contains("PATCH /v1beta1/counters/players {\"count\":\"0\",\"capacity\":\"100\"}"),
                calls.toString());

        lifecycle.heartbeat();
        assertEquals(List.of("POST /health {}"), sidecar.callsWithin(500));
    }

    @Test
    @DisplayName("A camp server publishes its camp slots instead of players")
    void campPublishesCampSlots() throws Exception {
        final AgonesLifecycle lifecycle = new AgonesLifecycle(AgonesSdk.at(sidecar.base()),
                new RoleGate(ServerRole.CAMP));
        lifecycle.onServerLoad(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));
        final List<String> startup = sidecar.callsWithin(500);
        assertFalse(startup.stream().anyMatch(call -> call.contains("counters/players")), startup.toString());

        lifecycle.onCampSlotsChanged(new CampSlotsChangedEvent(2, 10));
        assertEquals(List.of("PATCH /v1beta1/counters/camp-slots {\"count\":\"2\",\"capacity\":\"10\"}"),
                sidecar.callsWithin(500));
    }

    @Test
    @DisplayName("Shutdown is reported once")
    void shutdownOnce() throws Exception {
        final AgonesLifecycle lifecycle = new AgonesLifecycle(AgonesSdk.at(sidecar.base()),
                new RoleGate(ServerRole.RESOURCE));
        lifecycle.shutdown();
        lifecycle.shutdown();
        assertEquals(List.of("POST /shutdown {}"), sidecar.callsWithin(500));
    }

    @Test
    @DisplayName("With no sidecar the client stays off and sends nothing")
    void noSidecarStaysOff() throws Exception {
        final AgonesSdk sdk = AgonesSdk.fromEnvironment(Map.of());
        assertFalse(sdk.isEnabled());
        assertFalse(sdk.ready().join());

        final AgonesLifecycle lifecycle = new AgonesLifecycle(sdk, new RoleGate(ServerRole.ALL));
        lifecycle.onServerLoad(new ServerLoadEvent(ServerLoadEvent.LoadType.STARTUP));
        lifecycle.heartbeat();
        lifecycle.shutdown();
        bukkit.verify(Bukkit::getOnlinePlayers, never());
        bukkit.verify(Bukkit::getMaxPlayers, never());
        assertTrue(sidecar.callsWithin(300).isEmpty());
    }

    @Test
    @DisplayName("A port in the environment turns the client on")
    void portTurnsItOn() {
        assertTrue(AgonesSdk.fromEnvironment(Map.of(AgonesSdk.PORT_VARIABLE, "9358")).isEnabled());
        assertFalse(AgonesSdk.fromEnvironment(Map.of(AgonesSdk.PORT_VARIABLE, " ")).isEnabled());
    }

}
