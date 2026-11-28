package me.mykindos.betterpvp.core.framework.net;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Hosts decided through the {@link SiteDirectory}: an owned site stays with the first server to claim it, and a new
 * instance goes to the emptiest server reporting in.
 */
public class DirectorySiteHosts implements SiteHosts {

    private final SiteDirectory directory;

    public DirectorySiteHosts(@NotNull SiteDirectory directory) {
        this.directory = directory;
    }

    @Override
    public @NotNull CompletableFuture<Optional<String>> hostFor(@NotNull String siteId, long ownerId, boolean owned,
                                                                @NotNull String asker) {
        if (owned) {
            return directory.stickyHost(siteId, ownerId, asker).thenApply(Optional::of);
        }
        return directory.serversByLoad().thenApply(servers -> Optional.of(servers.isEmpty() ? asker : servers.get(0)));
    }
}
