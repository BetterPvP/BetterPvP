package me.mykindos.betterpvp.clans.clans.core.storage;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import lombok.Value;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.repository.ClanRepository;
import me.mykindos.betterpvp.clans.clans.writer.ClanWriter;
import me.mykindos.betterpvp.clans.database.jooq.tables.records.ClanMetadataRecord;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A clan's vault and mailbox, locked across every server through {@link ClanLocks}.
 * <p>
 * The server that takes the lock reads the stored contents before showing them, since the server that held it last
 * may have changed them. Letting go waits until the Clans service has stored the save, so whoever takes the lock next
 * reads what was saved.
 */
@CustomLog
@Singleton
public class ServiceClanInventories implements ClanInventories {

    private final ClanLocks locks;
    private final ClanWriter writer;
    private final ClanRepository repository;
    private final Executor mainThread;

    @Inject
    public ServiceClanInventories(@NotNull Clans clans, @NotNull ClanLocks locks, @NotNull ClanWriter writer,
                                  @NotNull ClanRepository repository) {
        this(locks, writer, repository, task -> UtilServer.runTask(clans, task));
    }

    public ServiceClanInventories(@NotNull ClanLocks locks, @NotNull ClanWriter writer,
                                  @NotNull ClanRepository repository, @NotNull Executor mainThread) {
        this.locks = locks;
        this.writer = writer;
        this.repository = repository;
        this.mainThread = mainThread;
    }

    @Override
    public @NotNull CompletableFuture<LockAnswer> open(@NotNull Clan clan, @NotNull Inventory inventory,
                                                       @NotNull String holder) {
        final String key = key(clan, inventory);
        return locks.take(key, holder)
                .thenApply(answer -> answer.isTaken() ? read(clan, inventory, key) : new Opening(answer, null))
                .thenApplyAsync(opening -> {
                    if (opening.getStored() != null) {
                        if (inventory == Inventory.VAULT) {
                            clan.getCore().getVault().read(opening.getStored());
                        } else {
                            clan.getCore().getMailbox().read(opening.getStored());
                        }
                    }
                    return opening.getAnswer();
                }, mainThread);
    }

    private @NotNull Opening read(@NotNull Clan clan, @NotNull Inventory inventory, @NotNull String key) {
        try {
            final ClanMetadataRecord metadata = repository.getMetadata(clan.getId());
            if (metadata == null) {
                return new Opening(LockAnswer.taken(), null);
            }
            return new Opening(LockAnswer.taken(),
                    inventory == Inventory.VAULT ? metadata.getVault() : metadata.getMailbox());
        } catch (RuntimeException ex) {
            log.error("Could not read the {} of clan {}, leaving it closed", inventory, clan.getId(), ex).submit();
            locks.release(key);
            return new Opening(LockAnswer.unreachable(), null);
        }
    }

    @Override
    public @NotNull CompletableFuture<Void> close(@NotNull Clan clan, @NotNull Inventory inventory) {
        final CompletableFuture<Void> save = inventory == Inventory.VAULT
                ? writer.saveVault(clan)
                : writer.saveMailbox(clan);
        return save.whenComplete((unused, error) -> locks.release(key(clan, inventory)));
    }

    private static @NotNull String key(@NotNull Clan clan, @NotNull Inventory inventory) {
        return clan.getId() + "." + inventory.name().toLowerCase(Locale.ROOT);
    }

    @Value
    private static class Opening {
        @NotNull LockAnswer answer;
        /** The stored contents, or null when nothing should be read. */
        @Nullable String stored;
    }
}
