package me.mykindos.betterpvp.core.world.site;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.framework.agones.ServerDrainEvent;
import me.mykindos.betterpvp.core.framework.net.PlayerTransfer;
import me.mykindos.betterpvp.core.framework.net.SiteDirectory;
import me.mykindos.betterpvp.core.framework.net.SiteHosts;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Moves every owned site this server holds to another server when it drains, with the players inside.
 * <p>
 * Each instance is handed off in order: its world closes with everyone moved out of it, its record is written down,
 * its lease is given up, and only then is it located again, so the server that opens it next reads what was saved
 * here and nothing changes after the save. Its players are then sent there. Anybody who cannot be placed goes to the
 * drain target like everyone else. Without a shared directory there is no other server, so nothing is handed off.
 */
@BPvPListener
@Singleton
@CustomLog
public class SiteDrainHandoff implements Listener {

    private final SiteInstances instances;
    private final SiteDirectory directory;
    private final SiteHosts hosts;
    private final Placement placement;
    private final PlayerTransfer transfers;

    @Inject
    public SiteDrainHandoff(@NotNull SiteInstances instances, @NotNull SiteDirectory directory,
                            @NotNull SiteHosts hosts, @NotNull Placement placement,
                            @NotNull PlayerTransfer transfers) {
        this.instances = instances;
        this.directory = directory;
        this.hosts = hosts;
        this.placement = placement;
        this.transfers = transfers;
    }

    @EventHandler
    public void onDrain(@NotNull ServerDrainEvent event) {
        if (!directory.isShared()) {
            return;
        }

        for (SiteInstance instance : List.copyOf(instances.all())) {
            if (!instance.getKey().isOwned()) {
                continue;
            }

            final List<Player> inside = playersIn(instance);
            inside.forEach(event::claim);
            handOff(instance, inside, event.getDrainTarget());
        }
    }

    /** Hands one instance to another server and sends the players who were in it after it. */
    @NotNull CompletableFuture<Boolean> handOff(@NotNull SiteInstance instance, @NotNull List<Player> inside,
                                                @NotNull String drainTarget) {
        final SiteKey key = instance.getKey();
        final CompletableFuture<Void> released = instances.handOff(instance.getId())
                .thenCompose(unused -> hosts.release(key.getSiteId(), key.getOwnerId(), Core.getCurrentRealm().getServer().getName()));
        if (inside.isEmpty()) {
            return released.thenApply(unused -> true);
        }

        final Set<UUID> members = new LinkedHashSet<>();
        inside.forEach(player -> members.add(player.getUniqueId()));
        final Party party = Party.of(inside.getFirst().getUniqueId(), members);

        return released
                .thenCompose(unused -> placement.locate(key, party))
                .thenCompose(handle -> placement.sendAll(stillOnline(inside), handle))
                .exceptionally(ex -> {
                    log.error("Could not move site {} off this server, sending its players to {}", key, drainTarget, ex).submit();
                    return false;
                })
                .thenCompose(placed -> Boolean.TRUE.equals(placed)
                        ? CompletableFuture.completedFuture(true)
                        : transfers.transferAll(stillOnline(inside), drainTarget).thenApply(unused -> false));
    }

    private @NotNull List<Player> playersIn(@NotNull SiteInstance instance) {
        return instance.getOccupants().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .toList();
    }

    private static @NotNull List<Player> stillOnline(@NotNull List<Player> players) {
        return players.stream().filter(Player::isOnline).toList();
    }
}
