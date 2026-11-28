package me.mykindos.betterpvp.clans.clans.core.storage;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.writer.ClanWriter;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/** A server that is the whole network, where the open vault or mailbox each clan keeps in memory is the only lock. */
@Singleton
public class LocalClanInventories implements ClanInventories {

    private final ClanWriter writer;

    @Inject
    public LocalClanInventories(@NotNull ClanWriter writer) {
        this.writer = writer;
    }

    @Override
    public @NotNull CompletableFuture<LockAnswer> open(@NotNull Clan clan, @NotNull Inventory inventory,
                                                       @NotNull String holder) {
        return CompletableFuture.completedFuture(LockAnswer.taken());
    }

    @Override
    public @NotNull CompletableFuture<Void> close(@NotNull Clan clan, @NotNull Inventory inventory) {
        return inventory == Inventory.VAULT ? writer.saveVault(clan) : writer.saveMailbox(clan);
    }
}
