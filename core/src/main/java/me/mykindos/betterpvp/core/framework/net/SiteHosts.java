package me.mykindos.betterpvp.core.framework.net;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Which server should host a site. An owned site gets the one server that holds it, and any other site gets a
 * server for one new instance.
 * <p>
 * Empty means nobody could decide right now, and a caller refuses to send anybody across servers rather than guess.
 */
public interface SiteHosts {

    /**
     * @param asker the server asking, which some implementations choose when nothing else is known
     */
    @NotNull CompletableFuture<Optional<String>> hostFor(@NotNull String siteId, long ownerId, boolean owned,
                                                         @NotNull String asker);
}
