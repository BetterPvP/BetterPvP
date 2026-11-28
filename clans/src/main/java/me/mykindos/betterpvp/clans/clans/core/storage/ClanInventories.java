package me.mykindos.betterpvp.clans.clans.core.storage;

import me.mykindos.betterpvp.clans.clans.Clan;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/** Opening and closing a clan's vault or mailbox, which one player at a time may have open. */
public interface ClanInventories {

    enum Inventory {
        VAULT,
        MAILBOX
    }

    /**
     * Takes {@code inventory} for {@code holder} and brings this server's copy of its contents up to date. Completes
     * on the main thread.
     */
    @NotNull CompletableFuture<LockAnswer> open(@NotNull Clan clan, @NotNull Inventory inventory,
                                                @NotNull String holder);

    /** Saves this server's copy of {@code inventory} and lets go of it once the save is stored. */
    @NotNull CompletableFuture<Void> close(@NotNull Clan clan, @NotNull Inventory inventory);
}
