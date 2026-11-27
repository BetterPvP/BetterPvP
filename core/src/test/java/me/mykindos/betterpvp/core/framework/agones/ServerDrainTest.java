package me.mykindos.betterpvp.core.framework.agones;

import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.framework.net.NetworkLayer;
import me.mykindos.betterpvp.core.framework.net.PlayerTransfer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Draining a server before it stops")
class ServerDrainTest {

    private final List<Player> online = new ArrayList<>();
    private final AgonesLifecycle lifecycle = mock(AgonesLifecycle.class);
    private final PlayerTransfer transfer = mock(PlayerTransfer.class);
    private final PluginManager plugins = mock(PluginManager.class);
    private MockedStatic<Bukkit> bukkit;
    private ServerDrain drain;

    @BeforeEach
    void setUp() throws Exception {
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(any(Core.class), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mock(BukkitTask.class));

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> List.copyOf(online));
        bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

        final NetworkLayer network = mock(NetworkLayer.class);
        when(network.transfer()).thenReturn(transfer);

        drain = new ServerDrain(mock(Core.class), lifecycle, AgonesSdk.at(URI.create("http://localhost:1/")), network);
        setField("drainTarget", "hub");
        setField("drainSeconds", 30);

        online.add(mock(Player.class));
        online.add(mock(Player.class));
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    @DisplayName("Players are moved off before the server stops")
    void playersMoveBeforeStop() {
        final List<Player> players = List.copyOf(online);
        drain.drain();

        verify(plugins).callEvent(any(ServerDrainEvent.class));
        bukkit.verify(Bukkit::savePlayers);
        verify(transfer).transferAll(eq(players), eq("hub"));

        drain.checkEmpty();
        verify(lifecycle, never()).shutdown();
        bukkit.verify(Bukkit::shutdown, never());

        online.clear();
        drain.checkEmpty();
        final InOrder order = inOrder(transfer, lifecycle);
        order.verify(transfer).transferAll(eq(players), eq("hub"));
        order.verify(lifecycle).shutdown();
        bukkit.verify(Bukkit::shutdown);
    }

    @Test
    @DisplayName("A server that cannot empty stops when the drain times out")
    void timesOut() throws Exception {
        setField("drainSeconds", 0);
        drain.drain();
        drain.checkEmpty();

        verify(lifecycle).shutdown();
        bukkit.verify(Bukkit::shutdown);
    }

    @Test
    @DisplayName("A draining server refuses arrivals")
    void refusesArrivals() {
        final AsyncPlayerPreLoginEvent before = mock(AsyncPlayerPreLoginEvent.class);
        drain.onArrival(before);
        verify(before, never()).disallow(any(AsyncPlayerPreLoginEvent.Result.class), any(Component.class));

        drain.drain();
        assertTrue(drain.isDraining());
        final AsyncPlayerPreLoginEvent during = mock(AsyncPlayerPreLoginEvent.class);
        drain.onArrival(during);
        verify(during).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(Component.class));
    }

    @Test
    @DisplayName("Draining twice moves players once")
    void drainsOnce() {
        drain.drain();
        drain.drain();
        verify(transfer).transferAll(any(), eq("hub"));
    }

    private void setField(String name, Object value) throws Exception {
        final Field field = ServerDrain.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(drain, value);
    }

}
