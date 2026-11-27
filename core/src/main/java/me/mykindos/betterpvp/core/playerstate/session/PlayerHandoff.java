package me.mykindos.betterpvp.core.playerstate.session;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.mykindos.betterpvp.core.playerstate.PlayerState;
import me.mykindos.betterpvp.core.playerstate.PlayerStateCapture;
import me.mykindos.betterpvp.core.playerstate.PlayerStateStore;
import me.mykindos.betterpvp.sessions.SessionProtocol;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Moves a player's state onto this server when they arrive and off it when they leave.
 * <p>
 * Arriving claims the player, fences the stored state at the lease's first version so the previous owner can no
 * longer save, then loads it. Leaving saves and then releases, and while the save cannot reach the store it is retried
 * for up to a minute with the player still held, so the next server waits for it. Every save under a lease uses a
 * higher version than
 * the last, and a newer lease's versions outrank an older one's, so a stale server's save is always rejected.
 */
@CustomLog
@Singleton
public class PlayerHandoff {

    private final PlayerLeases leases;
    private final PlayerStateStore store;
    private final PlayerStateCapture capture;

    private final Map<UUID, Ownership> owned = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerState> arriving = new ConcurrentHashMap<>();

    @Inject
    public PlayerHandoff(@NotNull PlayerLeases leases, @NotNull PlayerStateStore store,
                         @NotNull PlayerStateCapture capture) {
        this.leases = leases;
        this.store = store;
        this.capture = capture;
    }

    public boolean isActive() {
        return leases.isActive();
    }

    /**
     * Claims and loads the player before they join. Safe off the main thread.
     *
     * @return whether this server now owns the player
     */
    public @NotNull CompletableFuture<Boolean> arrive(@NotNull UUID player, @NotNull Duration wait) {
        return leases.claim(player, wait).thenCompose(epoch -> {
            if (epoch.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }

            final long fence = SessionProtocol.firstVersion(epoch.getAsLong());
            return store.fence(player, fence)
                    .thenCompose(ignored -> store.load(player))
                    .thenApply(state -> {
                        owned.put(player, new Ownership(epoch.getAsLong(), new AtomicLong(fence), System.currentTimeMillis()));
                        state.ifPresent(loaded -> arriving.put(player, loaded));
                        return true;
                    });
        });
    }

    /** Puts the loaded state onto the player as they join. Main thread. */
    public void join(@NotNull Player player) {
        final PlayerState state = arriving.remove(player.getUniqueId());
        if (state != null) {
            capture.apply(player, state);
        }
    }

    /**
     * Saves the player without giving them up. Captures on the calling thread, so call it on the main thread.
     *
     * @return whether the save was accepted
     */
    public @NotNull CompletableFuture<Boolean> save(@NotNull Player player) {
        final Ownership ownership = owned.get(player.getUniqueId());
        if (ownership == null) {
            return CompletableFuture.completedFuture(false);
        }
        return store.save(capture.capture(player, ownership.nextVersion()));
    }

    /** Saves the player and releases them so the next server can claim them. Captures on the calling thread. */
    public @NotNull CompletableFuture<Void> leave(@NotNull Player player) {
        final UUID id = player.getUniqueId();
        arriving.remove(id);
        final Ownership ownership = owned.remove(id);
        if (ownership == null) {
            return CompletableFuture.completedFuture(null);
        }

        final PlayerState state = capture.capture(player, ownership.nextVersion());
        return saveOnTheWayOut(state, player.getName(), System.currentTimeMillis() + Duration.ofMinutes(1).toMillis())
                .thenCompose(saved -> {
                    if (!saved) {
                        log.warn("The save of {} on the way out was stale and was not kept", player.getName()).submit();
                    }
                    return leases.release(id, ownership.getEpoch());
                })
                .thenAccept(released -> {
                    if (!released) {
                        log.warn("{} was no longer owned by this server when released", player.getName()).submit();
                    }
                });
    }

    /** Saves until the store answers, so a store outage delays the release instead of losing the state. */
    private @NotNull CompletableFuture<Boolean> saveOnTheWayOut(@NotNull PlayerState state, @NotNull String name,
                                                                long giveUpAt) {
        return store.save(state)
                .handle((saved, failure) -> {
                    if (failure == null) {
                        return CompletableFuture.completedFuture(saved);
                    }
                    if (System.currentTimeMillis() > giveUpAt) {
                        log.error("Could not save {} on the way out, releasing them with their last save", name, failure).submit();
                        return CompletableFuture.completedFuture(false);
                    }
                    log.warn("Could not save {} on the way out, trying again", name).submit();
                    // The same version again, so a save that did land the first time is not written twice
                    return CompletableFuture.supplyAsync(() -> null, CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS))
                            .thenCompose(unused -> saveOnTheWayOut(state, name, giveUpAt));
                })
                .thenCompose(next -> next);
    }

    /** Releases players that were claimed but never joined within {@code grace}, without saving them. */
    public void releaseAbandoned(@NotNull Set<UUID> online, @NotNull Duration grace) {
        final long cutoff = System.currentTimeMillis() - grace.toMillis();
        owned.forEach((id, ownership) -> {
            if (!online.contains(id) && ownership.getClaimedAt() < cutoff && owned.remove(id, ownership)) {
                arriving.remove(id);
                leases.release(id, ownership.getEpoch());
            }
        });
    }

    public @NotNull Set<UUID> owned() {
        return Set.copyOf(owned.keySet());
    }

    public void heartbeat() {
        leases.heartbeat();
    }

    @Getter
    @RequiredArgsConstructor
    private static final class Ownership {
        private final long epoch;
        private final AtomicLong lastVersion;
        private final long claimedAt;

        long nextVersion() {
            return lastVersion.incrementAndGet();
        }
    }
}
