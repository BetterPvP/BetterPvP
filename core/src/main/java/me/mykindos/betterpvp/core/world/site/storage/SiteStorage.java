package me.mykindos.betterpvp.core.world.site.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.mykindos.betterpvp.core.world.site.SiteKey;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Where a site's record is kept: everything about an instance that its world does not hold by itself.
 * <p>
 * A record is bytes as far as this is concerned. Only the module that wrote one knows what is in it, which is what
 * lets the store be swapped without anything above it changing. Every call answers a future.
 * <p>
 * Every record carries a version. A write names the version its writer last read, {@code 0} for a record that does
 * not exist yet, and is refused with {@link StaleSiteRecordException} when the record has moved on since. That is what
 * keeps a record to one writer: whoever wrote it last is the only one holding the version that may write it next.
 */
public interface SiteStorage {

    /** Shared so a record written by one caller reads back the same way for another. */
    ObjectMapper MAPPER = new ObjectMapper();

    @NotNull CompletableFuture<Optional<SiteRecord>> read(@NotNull SiteKey key);

    /**
     * Writes a record over the one at {@code version} and answers the version it is now at.
     */
    @NotNull CompletableFuture<Long> write(@NotNull SiteKey key, byte @NotNull [] data, long version);

    @NotNull CompletableFuture<Void> delete(@NotNull SiteKey key);

    /** Whether anything has ever been written for this site, without reading it. */
    @NotNull CompletableFuture<Boolean> exists(@NotNull SiteKey key);

    /** Releases whatever the store holds. */
    default void close() {
    }

    /** Writes an object as the bytes {@link #MAPPER} makes of it. */
    default @NotNull CompletableFuture<Long> write(@NotNull SiteKey key, @NotNull Object record, long version) {
        try {
            return write(key, MAPPER.writeValueAsBytes(record), version);
        } catch (Exception unwritable) {
            return CompletableFuture.failedFuture(unwritable);
        }
    }
}
