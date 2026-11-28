package me.mykindos.betterpvp.core.framework.agones;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Reports this server's lifecycle to whatever runs it. Every call completes with whether it was accepted, and
 * completes with {@code false} when {@link #isEnabled()} is false.
 */
public interface GameServerSdk {

    boolean isEnabled();

    @NotNull CompletableFuture<Boolean> ready();

    @NotNull CompletableFuture<Boolean> health();

    @NotNull CompletableFuture<Boolean> shutdown();

    @NotNull CompletableFuture<Boolean> setLabel(@NotNull String key, @NotNull String value);

    /**
     * Sets a counter, such as how many players or camps the server holds against how many it can.
     */
    @NotNull CompletableFuture<Boolean> setCounter(@NotNull String name, long count, long capacity);

}
