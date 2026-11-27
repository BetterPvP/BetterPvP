package me.mykindos.betterpvp.clans.clans.core.storage;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/** Locks every server sees, each held by one server at a time. */
public interface ClanLocks {

    /**
     * Takes {@code key} for {@code holder}, who is named to anyone else who asks for it. It stays taken until
     * {@link #release(String)}, or until this server fails to renew it.
     */
    @NotNull CompletableFuture<LockAnswer> take(@NotNull String key, @NotNull String holder);

    /** Lets go of a key this server took. */
    void release(@NotNull String key);
}
