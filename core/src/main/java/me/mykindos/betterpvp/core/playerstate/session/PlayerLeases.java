package me.mykindos.betterpvp.core.playerstate.session;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Which server owns each player. A server owns a player between a granted claim and its release. */
public interface PlayerLeases {

    /** Whether players are shared with other servers. When false, nothing is claimed, saved or loaded on a move. */
    boolean isActive();

    /**
     * Asks to own the player, waiting up to {@code wait} for whoever owns them to release them.
     *
     * @return the lease's epoch, or empty if another server still owns the player
     */
    @NotNull CompletableFuture<OptionalLong> claim(@NotNull UUID player, @NotNull Duration wait);

    /** @return whether this server owned the player under {@code epoch} and gave them up */
    @NotNull CompletableFuture<Boolean> release(@NotNull UUID player, long epoch);

    /** Keeps every lease this server holds alive. Called every few seconds. */
    void heartbeat();
}
