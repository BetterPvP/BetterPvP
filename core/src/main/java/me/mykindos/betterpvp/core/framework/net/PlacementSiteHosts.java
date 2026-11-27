package me.mykindos.betterpvp.core.framework.net;

import lombok.CustomLog;
import me.mykindos.betterpvp.network.protocol.PlacementMessages;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
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
        return bus.request(ask, Duration.ofSeconds(5)).thenApply(replies -> answer(siteId, ownerId, replies));
    }

    private @NotNull Optional<String> answer(@NotNull String siteId, long ownerId, @NotNull List<BusMessage> replies) {
        if (replies.isEmpty()) {
            log.warn("Placement did not answer for site {} of owner {}", siteId, ownerId).submit();
            return Optional.empty();
        }

        final BusMessage reply = replies.get(0);
        final Optional<String> server = reply.get(PlacementMessages.SERVER);
        if (server.isEmpty()) {
            log.warn("Placement could not place site {} of owner {}: {}", siteId, ownerId,
                    reply.getOrDefault(PlacementMessages.REASON, "no reason given")).submit();
        }
        return server;
    }
}
