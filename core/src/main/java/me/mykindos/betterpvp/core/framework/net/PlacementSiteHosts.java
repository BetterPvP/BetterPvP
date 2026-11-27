package me.mykindos.betterpvp.core.framework.net;

import lombok.CustomLog;
import me.mykindos.betterpvp.network.protocol.PlacementMessages;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Hosts decided by the Placement service, asked over the {@link MessageBus} in the format of
 * {@link PlacementMessages}.
 * <p>
 * No answer, or an answer naming no server, is empty. That happens when NATS or Placement cannot be reached, and
 * nothing is moved while it lasts.
 */
@CustomLog
public class PlacementSiteHosts implements SiteHosts {

    private final MessageBus bus;

    public PlacementSiteHosts(@NotNull MessageBus bus) {
        this.bus = bus;
    }

    @Override
    public @NotNull CompletableFuture<Optional<String>> hostFor(@NotNull String siteId, long ownerId, boolean owned,
                                                                @NotNull String asker) {
        final BusMessage ask = BusMessage.of(PlacementMessages.PLACE, Map.of(
                PlacementMessages.SITE, siteId,
                PlacementMessages.OWNER, String.valueOf(ownerId),
                PlacementMessages.OWNED, String.valueOf(owned),
                PlacementMessages.ASKER, asker));

        // Allocating a new server is a round trip to Kubernetes, so this waits longer than a chat lookup would.
        return bus.ask(ask, Duration.ofSeconds(5)).thenApply(reply -> answer(siteId, ownerId, reply));
    }

    @Override
    public @NotNull CompletableFuture<Void> release(@NotNull String siteId, long ownerId, @NotNull String server) {
        final BusMessage ask = BusMessage.of(PlacementMessages.RELEASE, Map.of(
                PlacementMessages.SITE, siteId,
                PlacementMessages.OWNER, String.valueOf(ownerId),
                PlacementMessages.SERVER, server));

        return bus.ask(ask, Duration.ofSeconds(5)).thenAccept(reply -> {
            if (reply.isEmpty()) {
                // The lease still lapses a minute after this server stops advertising the site.
                log.warn("Placement did not confirm releasing site {} of owner {}", siteId, ownerId).submit();
            }
        });
    }

    private @NotNull Optional<String> answer(@NotNull String siteId, long ownerId, @NotNull Optional<BusMessage> answered) {
        if (answered.isEmpty()) {
            log.warn("Placement did not answer for site {} of owner {}", siteId, ownerId).submit();
            return Optional.empty();
        }

        final BusMessage reply = answered.get();
        final Optional<String> server = reply.get(PlacementMessages.SERVER);
        if (server.isEmpty()) {
            log.warn("Placement could not place site {} of owner {}: {}", siteId, ownerId,
                    reply.getOrDefault(PlacementMessages.REASON, "no reason given")).submit();
        }
        return server;
    }
}
