package me.mykindos.betterpvp.clans.world.camp;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.world.site.SiteKey;
import me.mykindos.betterpvp.core.world.site.storage.SiteRecord;
import me.mykindos.betterpvp.core.world.site.storage.SiteStorage;
import me.mykindos.betterpvp.core.world.site.storage.StaleSiteRecordException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads and writes camp records, through whatever {@link SiteStorage} is bound.
 * <p>
 * A record is read once and held for as long as the clan is around, because the questions asked of it come from the
 * main thread while a world is being opened and cannot wait on a store that may be another machine away. Writing is
 * the other way round and never waited on.
 * <p>
 * Each record is written at the version it was read at, one write at a time per clan, so a record some other server
 * has written since is refused rather than overwritten. A refused record is read again, so this server holds what the
 * other one wrote and its next write lands on top of it.
 */
@Singleton
@CustomLog
public class CampStore {

    private final SiteStorage storage;
    private final Map<Long, Camp> records = new ConcurrentHashMap<>();
    private final Map<Long, Long> versions = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<Void>> writing = new ConcurrentHashMap<>();
    private final Set<Long> changed = ConcurrentHashMap.newKeySet();

    @Inject
    public CampStore(@NotNull SiteStorage storage) {
        this.storage = storage;
    }

    /**
     * Reads a clan's camp, or makes an empty one for a clan that has never had a camp before. Reading is what puts it
     * within reach of {@link #cached(long)}, so anything that will need it should ask well before it does.
     */
    public @NotNull CompletableFuture<Camp> load(long clanId) {
        final Camp known = records.get(clanId);
        if (known != null) {
            return CompletableFuture.completedFuture(known);
        }

        return storage.read(Camps.keyFor(clanId))
                .thenApply(found -> {
                    final Optional<Camp> camp = found.map(record -> record.as(Camp.class));
                    versions.putIfAbsent(clanId, found.map(SiteRecord::getVersion).orElse(0L));
                    return camp;
                })
                .exceptionally(ex -> {
                    // No version is known for a record that cannot be read, so every write of the empty camp held in
                    // its place is refused and the bad read never becomes a lost camp.
                    log.error("Could not read the camp record for clan {}", clanId, ex).submit();
                    return Optional.empty();
                })
                .thenApply(found -> records.computeIfAbsent(clanId, unused -> found.orElseGet(Camp::new)));
    }

    /** A clan's camp as it was last read, or empty when it has not been read yet. */
    public @NotNull Optional<Camp> cached(long clanId) {
        return Optional.ofNullable(records.get(clanId));
    }

    /** Notes that a clan's camp changed, so the next {@link #flush()} writes it down. */
    public void changed(long clanId) {
        changed.add(clanId);
    }

    /** Writes every camp that changed since the last flush. */
    public @NotNull CompletableFuture<Void> flush() {
        final List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (Long clanId : List.copyOf(changed)) {
            changed.remove(clanId);
            writes.add(save(clanId));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    /** Writes what is held for a clan. Does nothing for one whose record was never read. */
    public @NotNull CompletableFuture<Void> save(long clanId) {
        final Camp camp = records.get(clanId);
        if (camp == null) {
            return CompletableFuture.completedFuture(null);
        }

        changed.remove(clanId);
        final byte[] data;
        try {
            data = SiteStorage.MAPPER.writeValueAsBytes(camp);
        } catch (Exception ex) {
            log.error("Could not write the camp record for clan {}", clanId, ex).submit();
            return CompletableFuture.completedFuture(null);
        }

        return writing.compute(clanId, (id, previous) -> previous == null
                ? write(clanId, data)
                : previous.thenCompose(unused -> write(clanId, data)));
    }

    private @NotNull CompletableFuture<Void> write(long clanId, byte @NotNull [] data) {
        final Long version = versions.get(clanId);
        if (version == null) {
            log.warn("Not writing the camp record for clan {}, it was never read", clanId).submit();
            return CompletableFuture.completedFuture(null);
        }

        return storage.write(Camps.keyFor(clanId), data, version)
                .thenAccept(written -> versions.replace(clanId, version, written))
                .exceptionallyCompose(ex -> {
                    if (ex.getCause() instanceof StaleSiteRecordException) {
                        log.warn("Not writing the camp record for clan {}, it was written elsewhere since. Reading it again",
                                clanId).submit();
                        return reread(clanId);
                    }
                    log.error("Could not write the camp record for clan {}", clanId, ex).submit();
                    return CompletableFuture.completedFuture(null);
                });
    }

    private @NotNull CompletableFuture<Void> reread(long clanId) {
        return storage.read(Camps.keyFor(clanId))
                .thenAccept(found -> found.ifPresent(record -> {
                    if (records.containsKey(clanId)) {
                        records.put(clanId, record.as(Camp.class));
                        versions.put(clanId, record.getVersion());
                    }
                }))
                .exceptionally(ex -> {
                    log.error("Could not read the camp record for clan {} again", clanId, ex).submit();
                    return null;
                });
    }

    /** Throws a camp away for good, for a clan that no longer exists. */
    public void delete(long clanId) {
        records.remove(clanId);
        versions.remove(clanId);
        writing.remove(clanId);
        changed.remove(clanId);
        storage.delete(Camps.keyFor(clanId)).exceptionally(ex -> {
            log.error("Could not delete the camp record for clan {}", clanId, ex).submit();
            return null;
        });
    }

    /** Drops what is held in memory without touching the store. */
    public void forget(long clanId) {
        records.remove(clanId);
        versions.remove(clanId);
    }
}
