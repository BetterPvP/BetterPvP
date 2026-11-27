package me.mykindos.betterpvp.network.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The network player list")
class PlayerRosterTest {

    private final AtomicLong now = new AtomicLong();
    private final PlayerRoster roster = new PlayerRoster(Duration.ofSeconds(15), now::get);
    private final UUID alex = UUID.randomUUID();
    private final UUID sam = UUID.randomUUID();

    @Test
    @DisplayName("a player announced online is found on their proxy until announced offline")
    void onlineThenOffline() {
        roster.apply(PresenceMessages.ONLINE, Map.of(PresenceMessages.PLAYER, alex.toString(),
                PresenceMessages.NAME, "Alex", PresenceMessages.PROXY, "proxy-a"));

        assertEquals("proxy-a", roster.find(alex).orElseThrow().getProxy());
        assertEquals("Alex", roster.find(alex).orElseThrow().getName());

        roster.apply(PresenceMessages.OFFLINE, Map.of(PresenceMessages.PLAYER, alex.toString(),
                PresenceMessages.PROXY, "proxy-a"));

        assertTrue(roster.find(alex).isEmpty());
    }

    @Test
    @DisplayName("a proxy saying a player left does not remove them from another proxy")
    void offlineFromAnotherProxyIsIgnored() {
        roster.apply(PresenceMessages.ONLINE, Map.of(PresenceMessages.PLAYER, alex.toString(),
                PresenceMessages.NAME, "Alex", PresenceMessages.PROXY, "proxy-b"));
        roster.apply(PresenceMessages.OFFLINE, Map.of(PresenceMessages.PLAYER, alex.toString(),
                PresenceMessages.PROXY, "proxy-a"));

        assertEquals("proxy-b", roster.find(alex).orElseThrow().getProxy());
    }

    @Test
    @DisplayName("a roster replaces everything that proxy said before")
    void rosterReplaces() {
        roster.apply(PresenceMessages.ONLINE, Map.of(PresenceMessages.PLAYER, alex.toString(),
                PresenceMessages.NAME, "Alex", PresenceMessages.PROXY, "proxy-a"));

        roster.apply(PresenceMessages.ROSTER, Map.of(PresenceMessages.PROXY, "proxy-a",
                PresenceMessages.PLAYERS, PlayerRoster.encode(Map.of(sam, "Sam"))));

        assertTrue(roster.find(alex).isEmpty());
        assertEquals("Sam", roster.find(sam).orElseThrow().getName());
        assertEquals(1, roster.online().size());
    }

    @Test
    @DisplayName("a proxy that goes quiet takes its players with it")
    void quietProxyIsGone() {
        roster.apply(PresenceMessages.ROSTER, Map.of(PresenceMessages.PROXY, "proxy-a",
                PresenceMessages.PLAYERS, PlayerRoster.encode(Map.of(alex, "Alex"))));

        now.addAndGet(Duration.ofSeconds(16).toMillis());

        assertTrue(roster.find(alex).isEmpty());
        assertTrue(roster.online().isEmpty());
    }

    @Test
    @DisplayName("a roster survives being written and read back, empty included")
    void encodeDecode() {
        assertEquals(Map.of(alex, "Alex", sam, "Sam"), PlayerRoster.decode(PlayerRoster.encode(Map.of(alex, "Alex", sam, "Sam"))));
        assertTrue(PlayerRoster.decode("").isEmpty());
        assertTrue(PlayerRoster.decode("not-a-uuid=Bob").isEmpty());
    }
}
