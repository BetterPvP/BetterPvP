package me.mykindos.betterpvp.core.framework.net;

import me.mykindos.betterpvp.network.protocol.PlacementMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Asking the Placement service where a site lives")
class PlacementSiteHostsTest {

    private final RecordingMessageBus bus = new RecordingMessageBus();
    private final PlacementSiteHosts hosts = new PlacementSiteHosts(bus);

    @Test
    @DisplayName("the question names the site, its owner, whether it is owned and who asked")
    void askCarriesTheSite() {
        bus.replies = List.of(BusMessage.of(PlacementMessages.PLACE, Map.of(PlacementMessages.SERVER, "camp-7",
                PlacementMessages.OUTCOME, PlacementMessages.ALLOCATED)));

        final Optional<String> host = hosts.hostFor("camp", 42, true, "Clans-1").join();

        assertEquals(Optional.of("camp-7"), host);
        final BusMessage asked = bus.requested.get(0);
        assertEquals(PlacementMessages.PLACE, asked.getTopic());
        assertEquals(Map.of(PlacementMessages.SITE, "camp", PlacementMessages.OWNER, "42",
                PlacementMessages.OWNED, "true", PlacementMessages.ASKER, "Clans-1"), asked.getPayload());
    }

    @Test
    @DisplayName("silence means nobody decided, so nothing moves")
    void noAnswerIsEmpty() {
        assertTrue(hosts.hostFor("camp", 42, true, "Clans-1").join().isEmpty());
    }

    @Test
    @DisplayName("an answer that names no server is empty too")
    void unavailableIsEmpty() {
        bus.replies = List.of(BusMessage.of(PlacementMessages.PLACE, Map.of(
                PlacementMessages.OUTCOME, PlacementMessages.UNAVAILABLE,
                PlacementMessages.REASON, "no camp server could be allocated")));

        assertTrue(hosts.hostFor("camp", 42, true, "Clans-1").join().isEmpty());
    }
}
