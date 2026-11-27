package me.mykindos.betterpvp.orchestration.service.placement;

import lombok.extern.java.Log;
import me.mykindos.betterpvp.network.protocol.DirectoryKeys;
import me.mykindos.betterpvp.network.protocol.PlacementMessages;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.logging.Level;

/**
 * Decides which server hosts a site.
 * <p>
 * An owned site keeps the server its lease names while the lease is live. Otherwise it goes to the fullest running
 * server that still has a free slot, and failing that to a newly allocated one. The lease is written against the
 * revision read, so two decisions for the same site, here or on another Placement replica, settle on one server.
 * <p>
 * A lease is live while its host advertises the site, or for {@code leaseTtl} after the later of when it was written
 * and when NATS last became reachable. Time spent with NATS unreachable therefore never counts against a lease.
 * <p>
 * A host that releases a lease is draining, so it is given nothing new for the next ten minutes.
 */
@Log
public class Placer {

    private final LeaseStore leases;
    private final ServerPool pool;
    private final Function<String, String> roleOf;
    private final Duration leaseTtl;
    private final LongSupplier clock;

    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    /** Sites handed to a server that has not had time to count them yet, so its free slots read too high. */
    private final Map<String, Deque<Long>> promised = new ConcurrentHashMap<>();

    /** Servers that released a lease because they are draining, with when they did. */
    private final Map<String, Long> draining = new ConcurrentHashMap<>();

    private volatile long reachableSince;

    /**
     * @param roleOf the server role a site runs on
     * @param clock  epoch milliseconds
     */
    public Placer(LeaseStore leases, ServerPool pool, Function<String, String> roleOf, Duration leaseTtl,
                  LongSupplier clock) {
        this.leases = leases;
        this.pool = pool;
        this.roleOf = roleOf;
        this.leaseTtl = leaseTtl;
        this.clock = clock;
        this.reachableSince = clock.getAsLong();
    }

    /** Restarts every lease's clock, called whenever the connection to NATS comes back. */
    public void markReachable() {
        reachableSince = clock.getAsLong();
    }

    public PlacementDecision place(String siteId, long ownerId, boolean owned) {
        try {
            if (!owned) {
                return choose(roleOf.apply(siteId));
            }

            // One decision per site at a time here, so two asks never both allocate a server.
            synchronized (locks.computeIfAbsent(DirectoryKeys.site(siteId, ownerId), key -> new Object())) {
                return placeOwned(siteId, ownerId);
            }
        } catch (IOException exception) {
            log.log(Level.WARNING, "Could not place " + siteId + " for owner " + ownerId, exception);
            return PlacementDecision.unavailable(exception.getMessage());
        }
    }

    /**
     * Drops the lease on a site if it still names {@code server}, and gives that server nothing new while it drains.
     *
     * @return whether a lease naming {@code server} was dropped
     */
    public boolean release(String siteId, long ownerId, String server) {
        draining.put(server, clock.getAsLong());
        try {
            synchronized (locks.computeIfAbsent(DirectoryKeys.site(siteId, ownerId), key -> new Object())) {
                for (int attempt = 0; attempt < 4; attempt++) {
                    final Optional<Lease> current = leases.read(siteId, ownerId);
                    if (current.isEmpty() || !current.get().getServer().equals(server)) {
                        return false;
                    }
                    if (leases.release(siteId, ownerId, current.get())) {
                        return true;
                    }
                }
            }
        } catch (IOException exception) {
            log.log(Level.WARNING, "Could not release " + siteId + " for owner " + ownerId, exception);
        }
        return false;
    }

    private PlacementDecision placeOwned(String siteId, long ownerId) throws IOException {
        for (int attempt = 0; attempt < 4; attempt++) {
            final Optional<Lease> current = leases.read(siteId, ownerId);
            if (current.isPresent() && isLive(current.get(), siteId, ownerId)) {
                return PlacementDecision.of(PlacementMessages.HOSTED, current.get().getServer());
            }

            final PlacementDecision chosen = choose(roleOf.apply(siteId));
            if (!chosen.isPlaced()) {
                return chosen;
            }

            if (leases.claim(siteId, ownerId, chosen.getServer(), clock.getAsLong(), current.orElse(null))) {
                return chosen;
            }
            // Another replica wrote first. Reading again finds its lease and answers with that host.
        }
        return PlacementDecision.unavailable("the lease on " + siteId + " kept changing");
    }

    private boolean isLive(Lease lease, String siteId, long ownerId) throws IOException {
        if (leases.isAdvertised(siteId, ownerId, lease.getServer())) {
            return true;
        }
        final long since = Math.max(lease.getPlaced(), reachableSince);
        return clock.getAsLong() - since <= leaseTtl.toMillis();
    }

    private PlacementDecision choose(String role) throws IOException {
        final List<ServerPool.Slots> servers = pool.servers(role);
        final Optional<ServerPool.Slots> roomiest = servers.stream()
                .filter(slots -> free(slots) > 0)
                .filter(slots -> !isDraining(slots.getServer()))
                // The fullest with room first, so servers fill up and empty ones can be given back.
                .min(Comparator.comparingInt(this::free));
        if (roomiest.isPresent()) {
            promise(roomiest.get().getServer());
            return PlacementDecision.of(PlacementMessages.PLACED, roomiest.get().getServer());
        }

        final Optional<String> allocated = pool.allocate(role);
        if (allocated.isEmpty()) {
            return PlacementDecision.unavailable("no " + role + " server could be allocated");
        }
        promise(allocated.get());
        return PlacementDecision.of(PlacementMessages.ALLOCATED, allocated.get());
    }

    private boolean isDraining(String server) {
        final Long since = draining.get(server);
        if (since == null) {
            return false;
        }
        if (clock.getAsLong() - since > Duration.ofMinutes(10).toMillis()) {
            draining.remove(server);
            return false;
        }
        return true;
    }

    private int free(ServerPool.Slots slots) {
        return slots.getFree() - promisedTo(slots.getServer());
    }

    private void promise(String server) {
        final Deque<Long> times = promised.computeIfAbsent(server, key -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(clock.getAsLong());
        }
    }

    private int promisedTo(String server) {
        final Deque<Long> times = promised.get(server);
        if (times == null) {
            return 0;
        }

        // A server reports what it opened within a heartbeat or two, after which its own count is the truth.
        final long cutoff = clock.getAsLong() - Duration.ofSeconds(30).toMillis();
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < cutoff) {
                times.pollFirst();
            }
            return times.size();
        }
    }
}
