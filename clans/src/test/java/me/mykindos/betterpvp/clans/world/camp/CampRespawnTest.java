package me.mykindos.betterpvp.clans.world.camp;

import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.fatigue.RespawnHoldService;
import me.mykindos.betterpvp.clans.world.spawn.Spawn;
import me.mykindos.betterpvp.core.world.construction.ConstructionSites;
import me.mykindos.betterpvp.core.world.construction.StructureShapes;
import me.mykindos.betterpvp.core.world.site.Placement;
import me.mykindos.betterpvp.core.world.site.SiteHandle;
import me.mykindos.betterpvp.core.world.site.SiteInstance;
import me.mykindos.betterpvp.core.world.site.SiteInstances;
import me.mykindos.betterpvp.core.world.site.SiteKey;
import me.mykindos.betterpvp.core.world.site.SiteLandings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Respawning at the Barracks from any server")
class CampRespawnTest {

    private static final long CLAN = 9;
    private static final SiteKey CAMP = Camps.keyFor(CLAN);
    private static final SiteKey MAIN_SPAWN = SiteKey.of(Spawn.SITE_ID);

    private final ClanManager clanManager = mock(ClanManager.class);
    private final SiteInstances instances = mock(SiteInstances.class);
    private final Placement placement = mock(Placement.class);
    private final Player player = mock(Player.class);
    private final SiteHandle campHandle = new SiteHandle(UUID.randomUUID(), CAMP, "camp-b", "camps/" + CLAN);
    private final SiteHandle spawnHandle = new SiteHandle(UUID.randomUUID(), MAIN_SPAWN, "resource", "Season-2/Spawn");

    private MockedStatic<Bukkit> bukkit;
    private BukkitScheduler scheduler;
    private final List<Runnable> scheduled = new ArrayList<>();
    private CampRespawn respawn;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            scheduled.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Member");
        when(player.isOnline()).thenReturn(true);
        when(instances.forKey(any())).thenReturn(List.of());
        doReturn(CompletableFuture.completedFuture(true)).when(placement).send(any(), any());
        doReturn(CompletableFuture.completedFuture(true)).when(placement).send(any(), any(), any());

        respawn = new CampRespawn(mock(Clans.class), clanManager, mock(ConstructionSites.class),
                mock(StructureShapes.class), instances, placement, mock(RespawnHoldService.class), new SiteLandings());
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private void inClan() {
        final Clan clan = mock(Clan.class);
        when(clan.getId()).thenReturn(CLAN);
        when(clanManager.getClanByPlayer(player)).thenReturn(Optional.of(clan));
    }

    private void holdsMainSpawn() {
        when(instances.forKey(MAIN_SPAWN)).thenReturn(List.of(
                new SiteInstance(UUID.randomUUID(), MAIN_SPAWN, "Season-2/Spawn", SiteInstance.State.READY)));
    }

    /** Respawns the player and runs the wait for any respawn hold once, as the scheduler would a tick later. */
    private void die() {
        final PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
        when(event.getPlayer()).thenReturn(player);
        respawn.onRespawn(event);
        final List<Runnable> waits = List.copyOf(scheduled);
        scheduled.clear();
        waits.forEach(Runnable::run);
    }

    @Test
    @DisplayName("a member dying away from their camp is sent to its Barracks wherever Placement opens it")
    void aMemberElsewhereGoesToTheBarracks() {
        inClan();
        doReturn(CompletableFuture.completedFuture(campHandle)).when(placement).locate(eq(CAMP), any());

        die();

        verify(placement).send(player, campHandle, CampRespawn.LANDING);
        verify(placement, never()).locate(eq(MAIN_SPAWN), any());
    }

    @Test
    @DisplayName("a member whose camp cannot open goes to the main spawn on the server that holds it")
    void anUnreachableCampFallsBackToTheMainSpawn() {
        inClan();
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("no camp server")))
                .when(placement).locate(eq(CAMP), any());
        doReturn(CompletableFuture.completedFuture(spawnHandle)).when(placement).locate(eq(MAIN_SPAWN), any());

        die();

        verify(placement).send(player, spawnHandle);
    }

    @Test
    @DisplayName("a clanless player on a server without the main spawn is sent there")
    void aClanlessPlayerGoesToTheMainSpawn() {
        when(clanManager.getClanByPlayer(player)).thenReturn(Optional.empty());
        doReturn(CompletableFuture.completedFuture(spawnHandle)).when(placement).locate(eq(MAIN_SPAWN), any());

        die();

        verify(placement).send(player, spawnHandle);
    }

    @Test
    @DisplayName("where the main spawn is held here, a clanless or stranded player respawns as they always have")
    void theServerHoldingTheMainSpawnKeepsItsOwn() {
        holdsMainSpawn();
        when(clanManager.getClanByPlayer(player)).thenReturn(Optional.empty());

        die();

        verify(placement, never()).locate(any(), any());

        inClan();
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("no camp server")))
                .when(placement).locate(eq(CAMP), any());

        die();

        verify(placement, never()).locate(eq(MAIN_SPAWN), any());
    }
}
