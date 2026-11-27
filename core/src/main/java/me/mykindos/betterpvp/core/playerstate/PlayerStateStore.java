package me.mykindos.betterpvp.core.playerstate;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Saves and loads a player's state so any server can pick it up. Saves are versioned: a save is only accepted when its
 * version is higher than the stored one, so a server holding an older snapshot can never overwrite a newer one.
 */
public interface PlayerStateStore {

    /**
     * @return the latest saved state of the player, or empty if none has been saved
     */
    @NotNull CompletableFuture<Optional<PlayerState>> load(@NotNull UUID playerId);

    /**
     * @return true if the state was stored, false if a save with the same or a higher version is already stored
     */
    @NotNull CompletableFuture<Boolean> save(@NotNull PlayerState state);
}
