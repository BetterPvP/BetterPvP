package me.mykindos.betterpvp.core.network;

import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.framework.agones.ServerDrainEvent;
import me.mykindos.betterpvp.core.framework.net.PlayerTransfer;
import me.mykindos.betterpvp.core.framework.net.SiteDirectory;
import me.mykindos.betterpvp.core.playerstate.PlayerStateFixture;
import me.mykindos.betterpvp.core.playerstate.session.PlayerHandoffListener;
import me.mykindos.betterpvp.core.world.site.Placement;
import me.mykindos.betterpvp.core.world.site.SiteDrainHandoff;
import me.mykindos.betterpvp.core.world.site.SiteHandle;
import me.mykindos.betterpvp.core.world.site.SiteInstance;
import me.mykindos.betterpvp.core.world.site.SiteInstances;
import me.mykindos.betterpvp.core.world.site.SiteKey;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("A camp server that drains")
class CampDrainTest {

    private static final long CAMP = 42;

    private static Network network;

    @BeforeAll
    static void start() throws Exception {
        MockBukkit.mock();
        network = new Network();
    }

    @AfterAll
    static void stop() throws Exception {
        if (network != null) {
            network.close();
        }
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("hands its camp to another server and the campers follow it with everything they carried")
    void campAndCampersMove() throws Exception {
        network.offer("camp", "camp-a", "camp-b");
        final FakeServer draining = network.server("camp-a");
        final FakeServer next = network.server("camp-b");
        final PlayerStateFixture items = network.getFixture();
        assertEquals("camp-a", draining.hostOf("camp", CAMP, true));

        final SiteInstance instance = new SiteInstance(UUID.randomUUID(), SiteKey.of("camp", CAMP), "camp/" + CAMP,
                SiteInstance.State.READY);
        final Map<UUID, PlayerMock> campers = new ConcurrentHashMap<>();
        final Map<UUID, ItemStack> carried = new ConcurrentHashMap<>();
        for (int i = 0; i < 2; i++) {
            final UUID id = UUID.randomUUID();
            final PlayerMock camper = draining.arrive(id);
            final ItemStack item = items.blade(i);
            camper.getInventory().setItem(0, item);
            instance.addOccupant(id);
            campers.put(id, camper);
            carried.put(id, item);
        }
        draining.advertise("camp", CAMP, campers.size());

        final SiteInstances instances = mock(SiteInstances.class);
        when(instances.all()).thenReturn(List.of(instance));
        when(instances.handOff(instance.getId())).thenReturn(CompletableFuture.completedFuture(null));
        final SiteDirectory directory = mock(SiteDirectory.class);
        when(directory.isShared()).thenReturn(true);
        final PlayerTransfer fallback = mock(PlayerTransfer.class);
        final Set<UUID> claimed = ConcurrentHashMap.newKeySet();
        final Placement placement = placementOver(draining, Map.of("camp-b", next), claimed);

        final Plugin plugin = MockBukkit.createMockPlugin();
        MockBukkit.getMock().getPluginManager().registerEvents(new PlayerHandoffListener(draining.getHandoff()), plugin);
        MockBukkit.getMock().getPluginManager().registerEvents(
                new SiteDrainHandoff(instances, directory, draining.getHosts(), placement, fallback), plugin);
        try (MockedStatic<Core> core = mockStatic(Core.class, RETURNS_DEEP_STUBS)) {
            core.when(() -> Core.getCurrentRealm().getServer().getName()).thenReturn("camp-a");
            MockBukkit.getMock().getPluginManager().callEvent(new ServerDrainEvent("hub-1"));
            Network.await("camp-b to claim both campers", () -> claimed.equals(campers.keySet()));
        }
        // MockBukkit cannot answer whether the server is stopping, which the quit listener asks
        HandlerList.unregisterAll(plugin);

        for (Map.Entry<UUID, PlayerMock> camper : campers.entrySet()) {
            camper.getValue().disconnect();
            final PlayerMock arrived = next.join(camper.getKey());
            items.assertSameItem(carried.get(camper.getKey()), arrived.getInventory().getItem(0));
        }
        assertEquals(campers.keySet(), next.getHandoff().owned());
        assertEquals(Set.of(), draining.getHandoff().owned());
        assertEquals("camp-b", next.hostOf("camp", CAMP, true), "the camp's lease did not follow it");
        assertEquals("camp-b", next.hostOf("camp", CAMP + 1, true), "a draining server was given a new camp");
        verify(fallback, never()).transferAll(any(), anyString());
    }

    /**
     * Locates owned sites the way {@code NetworkPlacement} does, by asking placement for the host, and sends players
     * the way the proxy does, which starts with the next server claiming them. Joining them there is left to the test.
     */
    private static Placement placementOver(FakeServer asker, Map<String, FakeServer> servers, Set<UUID> claimed) {
        final Placement placement = mock(Placement.class);
        when(placement.locate(any(), any())).thenAnswer(call -> {
            final SiteKey key = call.getArgument(0);
            return asker.getHosts().hostFor(key.getSiteId(), key.getOwnerId(), true, asker.getName())
                    .thenApply(host -> new SiteHandle(UUID.randomUUID(), key, host.orElseThrow(), "camp/" + key.getOwnerId()));
        });
        when(placement.sendAll(any(), any())).thenAnswer(call -> {
            final Collection<Player> travellers = call.getArgument(0);
            final SiteHandle handle = call.getArgument(1);
            final FakeServer target = servers.get(handle.getServer());
            final List<CompletableFuture<Void>> moves = new ArrayList<>();
            for (Player traveller : travellers) {
                final UUID id = traveller.getUniqueId();
                moves.add(target.getHandoff().arrive(id, Duration.ofSeconds(20)).thenAccept(granted -> {
                    if (granted) {
                        claimed.add(id);
                    }
                }));
            }
            return CompletableFuture.allOf(moves.toArray(CompletableFuture[]::new)).thenApply(unused -> true);
        });
        return placement;
    }
}
