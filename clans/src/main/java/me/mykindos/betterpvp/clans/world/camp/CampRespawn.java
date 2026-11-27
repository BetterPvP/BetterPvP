package me.mykindos.betterpvp.clans.world.camp;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.fatigue.RespawnHoldService;
import me.mykindos.betterpvp.clans.world.camp.structure.CampStructures;
import me.mykindos.betterpvp.clans.world.spawn.Spawn;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.world.construction.ConstructionSites;
import me.mykindos.betterpvp.core.world.construction.StructureCondition;
import me.mykindos.betterpvp.core.world.construction.StructureShapes;
import me.mykindos.betterpvp.core.world.site.Party;
import me.mykindos.betterpvp.core.world.site.Placement;
import me.mykindos.betterpvp.core.world.site.SiteInstances;
import me.mykindos.betterpvp.core.world.site.SiteKey;
import me.mykindos.betterpvp.core.world.site.SiteLandings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Every clan member respawns at their own camp's Barracks, wherever they died, working or not. A camp loaded on this
 * server takes them straight there. Otherwise they come back at the normal spawn and are sent on to their camp,
 * landing at the Barracks. Players without a clan, and members whose camp cannot be reached, end up at the main spawn:
 * where they respawned when this server holds it, and sent to whichever server does otherwise. A
 * {@link CampRespawnEvent} lets another spot or landing stand in for the Barracks.
 */
@CustomLog
@BPvPListener
@Singleton
public class CampRespawn implements Listener {

    /** The point in a Barracks build members respawn on. */
    public static final String MARKER = "respawn";
    /** The landing a respawning member travels to. */
    public static final String LANDING = "barracks";

    private final Clans clans;
    private final ClanManager clanManager;
    private final ConstructionSites construction;
    private final StructureShapes shapes;
    private final SiteInstances instances;
    private final Placement placement;
    private final RespawnHoldService hold;

    @Inject
    public CampRespawn(@NotNull Clans clans, @NotNull ClanManager clanManager, @NotNull ConstructionSites construction,
                       @NotNull StructureShapes shapes, @NotNull SiteInstances instances, @NotNull Placement placement,
                       @NotNull RespawnHoldService hold, @NotNull SiteLandings landings) {
        this.clans = clans;
        this.clanManager = clanManager;
        this.construction = construction;
        this.shapes = shapes;
        this.instances = instances;
        this.placement = placement;
        this.hold = hold;
        landings.register(Camps.SITE_ID, LANDING, this::barracks);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final Optional<Clan> clan = clanManager.getClanByPlayer(player);
        if (clan.isEmpty()) {
            if (!holdsMainSpawn()) {
                travel(player, SiteKey.of(Spawn.SITE_ID), null);
            }
            return;
        }

        final SiteKey key = Camps.keyFor(clan.get());
        final Optional<World> world = instances.forKey(key).stream()
                .map(instance -> Bukkit.getWorld(instance.getWorldName()))
                .filter(Objects::nonNull)
                .findFirst();
        final CampRespawnEvent respawn = new CampRespawnEvent(player, key, world.orElse(null),
                world.flatMap(this::barracks).orElse(null), LANDING);
        respawn.callEvent();
        if (respawn.getSpot() != null) {
            event.setRespawnLocation(respawn.getSpot());
            return;
        }
        travel(player, key, respawn.getLanding());
    }

    /**
     * Sends a respawned player to {@code landing} in {@code key}, once any respawn hold has let them go. Failing that,
     * they go on to the main spawn if it is not here.
     */
    private void travel(@NotNull Player player, @NotNull SiteKey key, @Nullable String landing) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (hold.isHeld(player)) {
                    return;
                }
                cancel();
                final SiteKey mainSpawn = SiteKey.of(Spawn.SITE_ID);
                send(player, key, landing).thenAccept(arrived -> {
                    if (!arrived && !key.equals(mainSpawn) && !holdsMainSpawn()) {
                        send(player, mainSpawn, null);
                    }
                });
            }
        }.runTaskTimer(clans, 1L, 20L);
    }

    private @NotNull CompletableFuture<Boolean> send(@NotNull Player player, @NotNull SiteKey key,
                                                     @Nullable String landing) {
        return placement.locate(key, Party.solo(player.getUniqueId()))
                .thenCompose(handle -> landing == null
                        ? placement.send(player, handle)
                        : placement.send(player, handle, landing))
                .exceptionally(error -> {
                    log.warn("Could not send {} to {} after respawning", player.getName(), key, error).submit();
                    return false;
                });
    }

    private boolean holdsMainSpawn() {
        return !instances.forKey(SiteKey.of(Spawn.SITE_ID)).isEmpty();
    }

    /** Where the Barracks in {@code world} puts respawning members, broken or not. */
    public @NotNull Optional<Location> barracks(@NotNull World world) {
        return construction.worksite(world).stream()
                .flatMap(worksite -> worksite.getHolding().getStructures().stream())
                .filter(structure -> structure.getType().equals(CampStructures.BARRACKS))
                .filter(structure -> structure.getCondition() != StructureCondition.NOT_PLACED)
                .findFirst()
                .map(structure -> shapes.point(world, structure, MARKER)
                        .orElseGet(() -> structure.getPosition().toLocation(world).add(0.5, 1, 0.5)));
    }
}
