package me.mykindos.betterpvp.core.world.excursion;

import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.events.ClientJoinEvent;
import me.mykindos.betterpvp.core.framework.net.BusMessage;
import me.mykindos.betterpvp.core.framework.net.MessageBus;
import me.mykindos.betterpvp.core.framework.net.NetworkLayer;
import me.mykindos.betterpvp.core.framework.net.PlayerTransfer;
import me.mykindos.betterpvp.core.framework.net.SiteHosts;
import me.mykindos.betterpvp.core.server.Realm;
import me.mykindos.betterpvp.core.server.Season;
import me.mykindos.betterpvp.core.server.Server;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Taking a party to another server and back")
class NetworkExcursionsTest {

    private static final String ORIGIN = "Resource-1";
    private static final String DUNGEON = "Dungeon-1";

    private final List<BusMessage> published = new ArrayList<>();
    private final List<String> transfers = new ArrayList<>();
    private final List<ExcursionArrivalEvent> arrivals = new ArrayList<>();
    private final Map<UUID, Player> online = new HashMap<>();
    private final AtomicLong now = new AtomicLong(1_000);

    private Realm originalRealm;
    private MockedStatic<Bukkit> bukkit;
    private SiteHosts hosts;
    private NetworkExcursions origin;
    private NetworkExcursions dungeon;

    @BeforeEach
    void setUp() throws Exception {
        originalRealm = Core.getCurrentRealm();

        final PluginManager plugins = mock(PluginManager.class);
        doAnswer(call -> {
            final Event event = call.getArgument(0);
            if (event instanceof ExcursionArrivalEvent arrival) {
                arrivals.add(arrival);
            }
            return null;
        }).when(plugins).callEvent(any());

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> online.get(call.getArgument(0)));

        final MessageBus bus = mock(MessageBus.class);
        doAnswer(call -> published.add(call.getArgument(0))).when(bus).publish(any());

        final PlayerTransfer transfer = mock(PlayerTransfer.class);
        when(transfer.transfer(any(), anyString())).thenAnswer(call -> {
            transfers.add(((Player) call.getArgument(0)).getName() + " -> " + call.getArgument(1));
            return CompletableFuture.completedFuture(true);
        });
        when(transfer.transferAll(any(), anyString())).thenCallRealMethod();

        hosts = mock(SiteHosts.class);
        when(hosts.hostFor(anyString(), anyLong(), anyBoolean(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(DUNGEON)));

        final NetworkLayer network = mock(NetworkLayer.class);
        when(network.bus()).thenReturn(bus);
        when(network.transfer()).thenReturn(transfer);
        when(network.hosts()).thenReturn(hosts);

        origin = new NetworkExcursions(network, Runnable::run, now::get);
        dungeon = new NetworkExcursions(network, Runnable::run, now::get);
        gatherSeconds(origin, 20);
        gatherSeconds(dungeon, 20);
        on(ORIGIN);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
        Core.setCurrentRealm(originalRealm);
    }

    @Test
    @DisplayName("A party queuing a dungeon lands together on a dungeon server")
    void partyLandsTogether() {
        final List<Player> party = List.of(player("Leader"), player("Second"), player("Third"));
        final AtomicInteger paid = new AtomicInteger();

        assertTrue(origin.depart(party, "dungeon", Map.of("dungeon", "crypt"), paid::incrementAndGet).join());
        verify(hosts).hostFor("dungeon", 0L, false, ORIGIN);
        assertEquals(1, paid.get());
        assertEquals(List.of("Leader -> " + DUNGEON, "Second -> " + DUNGEON, "Third -> " + DUNGEON), transfers);

        on(DUNGEON);
        party.forEach(member -> online.remove(member.getUniqueId()));
        published.forEach(dungeon::onDeparture);

        join(dungeon, party.get(0));
        join(dungeon, party.get(1));
        assertTrue(arrivals.isEmpty(), "nobody starts until the whole party is in");

        join(dungeon, party.get(2));
        assertEquals(1, arrivals.size());
        final ExcursionArrivalEvent arrival = arrivals.getFirst();
        assertEquals(party, arrival.getTravellers());
        assertEquals(party.getFirst().getUniqueId(), arrival.getExcursion().getLeader());
        assertEquals("crypt", arrival.getExcursion().detail("dungeon", ""));
        assertEquals(ORIGIN, dungeon.visiting(party.get(1)).orElseThrow().getOrigin());
    }

    @Test
    @DisplayName("Without a server to go to nobody pays and nobody moves")
    void noServerNoTrip() {
        when(hosts.hostFor(anyString(), anyLong(), anyBoolean(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        final AtomicInteger paid = new AtomicInteger();

        assertFalse(origin.depart(List.of(player("Leader")), "dungeon", Map.of(), paid::incrementAndGet).join());
        assertEquals(0, paid.get());
        assertTrue(transfers.isEmpty());
        assertTrue(published.isEmpty());
    }

    @Test
    @DisplayName("A party missing a member starts without them once the wait runs out")
    void stragglersAreLeftBehind() {
        final List<Player> party = List.of(player("Leader"), player("Lost"));
        origin.depart(party, "dungeon", Map.of(), () -> { }).join();

        on(DUNGEON);
        online.clear();
        published.forEach(dungeon::onDeparture);
        join(dungeon, party.getFirst());

        dungeon.sweep();
        assertTrue(arrivals.isEmpty());

        now.addAndGet(20_000);
        dungeon.sweep();
        assertEquals(1, arrivals.size());
        assertEquals(List.of(party.getFirst()), arrivals.getFirst().getTravellers());
    }

    @Test
    @DisplayName("Going home puts a player back where they left from")
    void returnsHome() {
        final Player traveller = player("Leader");
        final Location left = traveller.getLocation();
        origin.depart(List.of(traveller), "dungeon", Map.of(), () -> { }).join();

        on(DUNGEON);
        online.clear();
        published.forEach(dungeon::onDeparture);
        join(dungeon, traveller);
        transfers.clear();

        assertTrue(dungeon.returnHome(traveller));
        assertEquals(List.of("Leader -> " + ORIGIN), transfers);
        assertFalse(dungeon.returnHome(traveller), "a second return has nowhere to go");

        on(ORIGIN);
        join(origin, traveller);
        verify(traveller).teleportAsync(left);
    }

    @Test
    @DisplayName("When the site runs on this server the party never leaves")
    void staysWhenHostedHere() {
        when(hosts.hostFor(anyString(), anyLong(), anyBoolean(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(ORIGIN)));
        final Player traveller = player("Leader");
        final Location left = traveller.getLocation();

        assertTrue(origin.depart(List.of(traveller), "dungeon", Map.of(), () -> { }).join());
        assertTrue(transfers.isEmpty());
        assertTrue(published.isEmpty());
        assertEquals(1, arrivals.size());

        assertTrue(origin.returnHome(traveller));
        verify(traveller).teleportAsync(left);
    }

    private Player player(String name) {
        final Player player = mock(Player.class);
        final UUID id = UUID.randomUUID();
        final Location location = new Location(mock(World.class), 1, 64, 1);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(location);
        when(player.teleportAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(true));
        online.put(id, player);
        return player;
    }

    private void join(NetworkExcursions server, Player player) {
        online.put(player.getUniqueId(), player);
        server.onJoin(new ClientJoinEvent(mock(Client.class), player));
    }

    private void on(String server) {
        Core.setCurrentRealm(new Realm(1, new Server(1, server), new Season(1, "test", LocalDate.now())));
    }

    private void gatherSeconds(NetworkExcursions excursions, int seconds) throws Exception {
        final Field field = NetworkExcursions.class.getDeclaredField("gatherSeconds");
        field.setAccessible(true);
        field.set(excursions, seconds);
    }
}
