package me.mykindos.betterpvp.core.world.site.storage;

import me.mykindos.betterpvp.core.world.site.SiteKey;
import org.jetbrains.annotations.NotNull;

/** A write was refused because the record was written by someone else since the writer read it. */
public class StaleSiteRecordException extends RuntimeException {

    public StaleSiteRecordException(@NotNull SiteKey key, long version) {
        super("The site record for " + key + " is no longer at version " + version);
    }
}
