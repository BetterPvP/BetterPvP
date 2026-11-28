package me.mykindos.betterpvp.core.playerstate.session;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** A server that shares players with nobody, so it always owns everyone on it. */
public class LocalPlayerLeases implements PlayerLeases {

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public @NotNull CompletableFuture<OptionalLong> claim(@NotNull UUID player, @NotNull Duration wait) {
        return CompletableFuture.completedFuture(OptionalLong.of(0));
    }

    @Override
    public @NotNull CompletableFuture<Boolean> release(@NotNull UUID player, long epoch) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public void heartbeat() {
    }
}
