package me.mykindos.betterpvp.core.world.site.storage;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A site's record as it was read, with the version it was read at. Writing it back means handing that version to
 * a {@link SiteStorage} write.
 */
@Value
public class SiteRecord {

    byte @NotNull [] data;

    long version;

    /** Reads the record back as the type that wrote it. */
    public <T> @NotNull T as(@NotNull Class<T> type) {
        try {
            return SiteStorage.MAPPER.readValue(data, type);
        } catch (Exception unreadable) {
            throw new IllegalStateException("Unreadable site record as " + type.getSimpleName(), unreadable);
        }
    }
}
