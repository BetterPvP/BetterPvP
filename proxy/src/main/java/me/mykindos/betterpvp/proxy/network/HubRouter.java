package me.mykindos.betterpvp.proxy.network;

import me.mykindos.betterpvp.network.protocol.PlacementMessages;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Asks Placement which hub a joining player should land on. */
public class HubRouter {

    private final ProxyBus bus;
    private final String hubSite;
    private final Logger logger;

    public HubRouter(ProxyBus bus, String hubSite, Logger logger) {
        this.bus = bus;
        this.hubSite = hubSite;
        this.logger = logger;
    }

    /** The hub's server name, or empty when Placement could not be asked or had nowhere. */
    public CompletableFuture<Optional<String>> hub() {
        return bus.ask(PlacementMessages.PLACE, Map.of(
                        PlacementMessages.SITE, hubSite,
                        PlacementMessages.OWNER, "0",
                        PlacementMessages.OWNED, "false",
                        PlacementMessages.ASKER, bus.origin()),
                        // Allocating a hub is a round trip to Kubernetes.
                        Duration.ofSeconds(5))
                .thenApply(answer -> {
                    final Optional<String> server = Optional.ofNullable(answer.get(PlacementMessages.SERVER));
                    if (server.isEmpty()) {
                        logger.warn("Placement has no hub to offer: {}", answer.get(PlacementMessages.REASON));
                    }
                    return server;
                })
                .exceptionally(error -> {
                    logger.warn("Could not ask Placement for a hub", error);
                    return Optional.empty();
                });
    }
}
