package me.mykindos.betterpvp.core.world.excursion;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import lombok.Value;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.events.ClientJoinEvent;
import me.mykindos.betterpvp.core.client.events.ClientQuitEvent;
import me.mykindos.betterpvp.core.config.Config;
import me.mykindos.betterpvp.core.framework.net.BusMessage;
import me.mykindos.betterpvp.core.framework.net.NetworkLayer;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.core.world.site.SiteKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * Excursions over the {@link NetworkLayer}: Placement picks the server, the bus tells it who is coming, and the
 * transfer moves them. Each server keeps the spot its own players left from, so it can put them back on return.
 */
@BPvPListener
@Singleton
@CustomLog
public class NetworkExcursions implements Excursions, Listener {

    private final NetworkLayer network;
    private final Executor mainThread;
    private final LongSupplier clock;

    @Inject
    @Config(path = "core.excursion.gatherSeconds", defaultValue = "20")
    private int gatherSeconds;

    /** Parties on their way here, by excursion. */
    private final Map<UUID, Gathering> expected = new ConcurrentHashMap<>();
    /** Players here on an excursion. */
    private final Map<UUID, Excursion> visitors = new ConcurrentHashMap<>();
    /** Where this server's own players left from, for when they come back. */
    private final Map<UUID, Location> departedFrom = new ConcurrentHashMap<>();

    @Inject
    public NetworkExcursions(@NotNull Core core, @NotNull NetworkLayer network) {
        this(network, task -> UtilServer.runTask(core, task), System::currentTimeMillis);
    }

    NetworkExcursions(@NotNull NetworkLayer network, @NotNull Executor mainThread, @NotNull LongSupplier clock) {
        this.network = network;
        this.mainThread = mainThread;
        this.clock = clock;
    }

    @EventHandler
    public void onServerLoad(@NotNull ServerLoadEvent event) {
        if (event.getType() == ServerLoadEvent.LoadType.STARTUP) {
            network.bus().subscribe("excursion.depart", this::onDeparture);
        }
    }

    @Override
    public @NotNull CompletableFuture<Boolean> depart(@NotNull Collection<Player> party, @NotNull String site,
                                                      @NotNull Map<String, String> details,
                                                      @NotNull Runnable beforeLeaving) {
        if (party.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        final String here = currentServer();
        return network.hosts().hostFor(site, SiteKey.NO_OWNER, false, here)
                .thenComposeAsync(host -> {
                    final List<Player> travellers = party.stream().filter(Player::isOnline).toList();
                    if (host.isEmpty() || travellers.isEmpty()) {
                        log.warn("No server could take a party of {} to {}", party.size(), site).submit();
                        return CompletableFuture.completedFuture(false);
                    }

                    final Excursion excursion = new Excursion(UUID.randomUUID(), site, here, host.get(),
                            travellers.stream().map(Player::getUniqueId).toList(), Map.copyOf(details));
                    beforeLeaving.run();
                    travellers.forEach(traveller -> departedFrom.put(traveller.getUniqueId(), traveller.getLocation()));

                    if (host.get().equals(here)) {
                        arrive(excursion, travellers);
                        return CompletableFuture.completedFuture(true);
                    }

                    network.bus().publish(BusMessage.of("excursion.depart", excursion.toPayload()));
                    return network.transfer().transferAll(travellers, host.get());
                }, mainThread)
                .exceptionally(error -> {
                    log.error("Could not send a party to {}", site, error).submit();
                    return false;
                });
    }

    @Override
    public boolean returnHome(@NotNull Player player) {
        final Excursion excursion = visitors.remove(player.getUniqueId());
        if (excursion == null) {
            return false;
        }

        if (excursion.getOrigin().equals(currentServer())) {
            final Location spot = departedFrom.remove(player.getUniqueId());
            if (spot != null) {
                player.teleportAsync(spot);
            }
            return spot != null;
        }

        network.transfer().transfer(player, excursion.getOrigin());
        return true;
    }

    @Override
    public @NotNull Optional<Excursion> visiting(@NotNull Player player) {
        return Optional.ofNullable(visitors.get(player.getUniqueId()));
    }

    void onDeparture(@NotNull BusMessage message) {
        final Excursion excursion = Excursion.fromPayload(message);
        if (!excursion.getDestination().equals(currentServer()) || excursion.getOrigin().equals(currentServer())) {
            return;
        }

        expected.put(excursion.getId(), new Gathering(excursion, clock.getAsLong() + gatherSeconds * 1000L));
        // Somebody may have connected before the message did.
        mainThread.execute(() -> excursion.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(this::arrived));
    }

    // After Residency and Placement have put the player somewhere, so the spot they left from wins.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull ClientJoinEvent event) {
        final Player player = event.getPlayer();
        if (arrived(player)) {
            return;
        }

        final Location spot = departedFrom.remove(player.getUniqueId());
        if (spot != null) {
            player.teleportAsync(spot);
        }
    }

    @EventHandler
    public void onQuit(@NotNull ClientQuitEvent event) {
        visitors.remove(event.getPlayer().getUniqueId());
    }

    /** Lets in whoever made it once the wait for stragglers is over. */
    @UpdateEvent(delay = 1000)
    public void sweep() {
        final long now = clock.getAsLong();
        expected.values().removeIf(gathering -> {
            if (now < gathering.getDeadline()) {
                return false;
            }
            final List<Player> present = present(gathering);
            if (!present.isEmpty()) {
                arrive(gathering.getExcursion(), present);
            }
            return true;
        });
    }

    private boolean arrived(@NotNull Player player) {
        for (Gathering gathering : expected.values()) {
            if (!gathering.getExcursion().getMembers().contains(player.getUniqueId())) {
                continue;
            }

            gathering.getPresent().add(player.getUniqueId());
            if (gathering.getPresent().size() == gathering.getExcursion().getMembers().size()
                    && expected.remove(gathering.getExcursion().getId()) != null) {
                arrive(gathering.getExcursion(), present(gathering));
            }
            return true;
        }
        return false;
    }

    /** Who of a party is here, in the party's own order so its leader stays first. */
    private @NotNull List<Player> present(@NotNull Gathering gathering) {
        return gathering.getExcursion().getMembers().stream()
                .filter(gathering.getPresent()::contains)
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .toList();
    }

    private void arrive(@NotNull Excursion excursion, @NotNull List<Player> travellers) {
        travellers.forEach(traveller -> visitors.put(traveller.getUniqueId(), excursion));
        UtilServer.callEvent(new ExcursionArrivalEvent(excursion, travellers));
    }

    private static @NotNull String currentServer() {
        return Core.getCurrentRealm().getServer().getName();
    }

    @Value
    private static class Gathering {
        Excursion excursion;
        long deadline;
        Set<UUID> present = new HashSet<>();
    }
}
