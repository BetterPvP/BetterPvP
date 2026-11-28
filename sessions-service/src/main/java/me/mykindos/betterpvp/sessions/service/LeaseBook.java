package me.mykindos.betterpvp.sessions.service;

import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Which server owns each player and which proxy each account is on.
 * <p>
 * A lease lasts {@code leaseMillis} past the owner's last heartbeat, so a server that stops answering loses its
 * players once that passes. Every grant gets an epoch from the clock, higher than any earlier epoch for that player,
 * so save versions taken from it stay ordered across service restarts.
 * <p>
 * Hearing from nobody for longer than a lease means this service was cut off, not that every holder died, so the
 * first message after such a silence gives every lease and proxy a fresh start before anything is decided.
 */
public class LeaseBook {

    private final long leaseMillis;
    private final long proxyTimeoutMillis;
    private final LongSupplier clock;

    private final Map<UUID, Lease> leases = new HashMap<>();
    private final Map<UUID, Long> lastEpochs = new HashMap<>();
    private final Map<UUID, String> proxies = new HashMap<>();
    private final Map<String, Long> proxySeen = new HashMap<>();
    private long lastHeard;

    public LeaseBook(long leaseMillis, long proxyTimeoutMillis, @NotNull LongSupplier clock) {
        this.leaseMillis = leaseMillis;
        this.proxyTimeoutMillis = proxyTimeoutMillis;
        this.clock = clock;
        this.lastHeard = clock.getAsLong();
    }

    /** Grants the player to {@code server} unless a live lease is held, even by {@code server} itself. */
    public synchronized @NotNull Claim claim(@NotNull UUID player, @NotNull String server) {
        final long now = hear();
        final Lease held = leases.get(player);
        if (held != null && held.expiresAt > now) {
            return Claim.held(held.server);
        }

        final long epoch = Math.max(now, lastEpochs.getOrDefault(player, 0L) + 1);
        lastEpochs.put(player, epoch);
        leases.put(player, new Lease(server, epoch, now + leaseMillis));
        return Claim.granted(epoch);
    }

    /** @return whether {@code server} held the lease under {@code epoch} and gave it up */
    public synchronized boolean release(@NotNull UUID player, @NotNull String server, long epoch) {
        hear();
        final Lease held = leases.get(player);
        if (held == null || held.epoch != epoch || !held.server.equals(server)) {
            return false;
        }
        leases.remove(player);
        return true;
    }

    /** Keeps every lease {@code server} holds alive. */
    public synchronized void heartbeat(@NotNull String server) {
        final long expiresAt = hear() + leaseMillis;
        for (Lease lease : leases.values()) {
            if (lease.server.equals(server)) {
                lease.expiresAt = expiresAt;
            }
        }
    }

    /** @return whether the account may join through {@code proxy}, binding it there when it may */
    public synchronized boolean login(@NotNull UUID player, @NotNull String proxy) {
        final long now = hear();
        proxySeen.put(proxy, now);
        final String bound = proxies.get(player);
        if (bound != null && !bound.equals(proxy) && now - proxySeen.getOrDefault(bound, 0L) < proxyTimeoutMillis) {
            return false;
        }
        proxies.put(player, proxy);
        return true;
    }

    public synchronized void logout(@NotNull UUID player, @NotNull String proxy) {
        proxySeen.put(proxy, hear());
        proxies.remove(player, proxy);
    }

    public synchronized void proxyHeartbeat(@NotNull String proxy) {
        proxySeen.put(proxy, hear());
    }

    /** @return the time now, after restarting every lease and proxy if nothing was heard for longer than they last */
    private long hear() {
        final long now = clock.getAsLong();
        if (now - lastHeard > leaseMillis) {
            for (Lease lease : leases.values()) {
                lease.expiresAt = Math.max(lease.expiresAt, now + leaseMillis);
            }
        }
        if (now - lastHeard > proxyTimeoutMillis) {
            proxySeen.replaceAll((proxy, seen) -> now);
        }
        lastHeard = now;
        return now;
    }

    @AllArgsConstructor
    private static final class Lease {
        private final String server;
        private final long epoch;
        private long expiresAt;
    }
}
