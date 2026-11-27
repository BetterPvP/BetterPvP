package me.mykindos.betterpvp.orchestration.service.placement;

import lombok.Value;
import me.mykindos.betterpvp.network.protocol.PlacementMessages;

/** What Placement answered: an outcome from {@link PlacementMessages}, and the server or the reason. */
@Value
public class PlacementDecision {

    String outcome;
    String server;
    String reason;

    public static PlacementDecision of(String outcome, String server) {
        return new PlacementDecision(outcome, server, null);
    }

    public static PlacementDecision unavailable(String reason) {
        return new PlacementDecision(PlacementMessages.UNAVAILABLE, null, reason);
    }

    public boolean isPlaced() {
        return server != null;
    }
}
