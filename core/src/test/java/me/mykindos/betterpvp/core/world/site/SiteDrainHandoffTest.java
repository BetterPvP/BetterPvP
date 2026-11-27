package me.mykindos.betterpvp.core.world.site;

import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.framework.agones.ServerDrainEvent;
import me.mykindos.betterpvp.core.framework.net.PlayerTransfer;
import me.mykindos.betterpvp.core.framework.net.SiteDirectory;
import me.mykindos.betterpvp.core.framework.net.SiteHosts;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Handing a draining server's camps to another server")
class SiteDrainHandoffTest {

    private final List<String> steps = new ArrayList<>();
    private final SiteInstances instances = mock(SiteInstances.class);
    private final SiteDirectory directory = mock(SiteDirectory.class);
    private final SiteHosts hosts = mock(SiteHosts.class);
    private final Placement placement = mock(Placement.class);
    private final PlayerTransfer transfers = mock(PlayerTransfer.class);
    private final SiteDrainHandoff handoff = new SiteDrainHandoff(instances, directory, hosts, placement, transfers);

    private MockedStatic<Core> core;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        core = mockStatic(Core.class, RETURNS_DEEP_STUBS);
        core.when(() -> Core.getCurrentRealm().getServer().getName()).thenReturn("camp-a");
        bukkit = mockStatic(Bukkit.class);
        when(directory.isShared()).thenReturn(true);

        when(hosts.release(anyString(), anyLong(), anyString())).thenAnswer(call -> {
            steps.add("release " + call.getArgument(1) + " from " + call.getArgument(2));
            return CompletableFuture.completedFuture(null);
        });
        when(placement.locate(any(), any())).thenAnswer(call -> {
            final SiteKey key = call.getArgument(0);
            steps.add("locate " + key.getOwnerId());
            return CompletableFuture.completedFuture(
                    new SiteHandle(UUID.randomUUID(), key, "camp-b", "camps/" + key.getOwnerId()));
        });
        when(placement.sendAll(any(), any())).thenAnswer(call -> {
            final List<Player> sent = List.copyOf(call.getArgument(0));
            final SiteHandle handle = call.getArgument(1);
            steps.add("send " + sent.size() + " to " + handle.getServer());
            return CompletableFuture.completedFuture(true);
        });
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
        core.close();
    }

    private SiteInstance camp(long clan, CompletableFuture<Void> saved, Player... inside) {
        final SiteInstance instance = new SiteInstance(UUID.randomUUID(), SiteKey.of("camp", clan), "camps/" + clan,
                SiteInstance.State.READY);
        for (Player player : inside) {
            instance.addOccupant(player.getUniqueId());
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
        }
        when(instances.handOff(instance.getId())).thenAnswer(call -> {
            steps.add("close and save " + clan);
            return saved;
        });
        return instance;
    }

    private static Player player() {
        final Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    @Test
    @DisplayName("draining a camp server moves every camp and player with nothing lost")
    void drainingMovesEveryCampAndPlayer() {
        final Player first = player();
        final Player second = player();
        final Player third = player();
        final CompletableFuture<Void> firstSaved = new CompletableFuture<>();
        final CompletableFuture<Void> secondSaved = new CompletableFuture<>();
        final CompletableFuture<Void> emptySaved = new CompletableFuture<>();
        final SiteInstance one = camp(1, firstSaved, first, second);
        final SiteInstance two = camp(2, secondSaved, third);
        final SiteInstance empty = camp(3, emptySaved);
        final SiteInstance island = new SiteInstance(UUID.randomUUID(), SiteKey.of("isle"), "sites/isle/x",
                SiteInstance.State.READY);
        when(instances.all()).thenReturn(List.of(one, two, empty, island));

        final ServerDrainEvent event = new ServerDrainEvent("hub");
        handoff.onDrain(event);

        assertTrue(event.isClaimed(first) && event.isClaimed(second) && event.isClaimed(third),
                "camp players are moved by the hand-off, not sent to the hub");
        verify(hosts, never()).release(anyString(), anyLong(), anyString());
        verify(placement, never()).locate(any(), any());

        // Nothing is given up or reopened until its record is written down.
        firstSaved.complete(null);
        secondSaved.complete(null);
        emptySaved.complete(null);

        assertEquals(List.of(
                "close and save 1", "close and save 2", "close and save 3",
                "release 1 from camp-a", "locate 1", "send 2 to camp-b",
                "release 2 from camp-a", "locate 2", "send 1 to camp-b",
                "release 3 from camp-a"), steps);
        verify(instances, never()).handOff(island.getId());
        verify(transfers, never()).transferAll(any(), eq("hub"));
    }

    @Test
    @DisplayName("players whose camp cannot be placed anywhere go to the drain target")
    void unplacedPlayersGoToTheDrainTarget() {
        final Player stuck = player();
        final SiteInstance one = camp(1, CompletableFuture.completedFuture(null), stuck);
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("full"))).when(placement).locate(any(), any());
        when(transfers.transferAll(any(), eq("hub"))).thenReturn(CompletableFuture.completedFuture(true));

        assertFalse(handoff.handOff(one, List.of(stuck), "hub").join());
        verify(transfers).transferAll(List.of(stuck), "hub");
    }

    @Test
    @DisplayName("a server with no shared directory hands nothing off")
    void aLoneServerKeepsItsCamps() {
        when(directory.isShared()).thenReturn(false);
        final Player inside = player();
        final SiteInstance one = camp(1, CompletableFuture.completedFuture(null), inside);
        when(instances.all()).thenReturn(List.of(one));

        final ServerDrainEvent event = new ServerDrainEvent("hub");
        handoff.onDrain(event);

        assertFalse(event.isClaimed(inside));
        verify(instances, never()).handOff(any());
    }
}
