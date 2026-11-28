package me.mykindos.betterpvp.core.network;

import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.framework.economy.ledger.Movement;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("NATS going down")
class NatsOutageTest {

    private static Network network;

    @BeforeAll
    static void start() throws Exception {
        MockBukkit.mock();
        network = new Network();
    }

    @AfterAll
    static void stop() throws Exception {
        if (network != null) {
            network.close();
        }
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("leaves servers running and every player where they were, even past the lease")
    void playersStayPut() throws Exception {
        final FakeServer hub = network.server("hub-1");
        final FakeServer other = network.server("hub-2");
        final UUID id = UUID.randomUUID();
        final PlayerMock player = hub.arrive(id);
        player.setLevel(3);
        final AtomicLong coins = new AtomicLong(10);
        final String account = LedgerAccounts.player(id);
        assertTrue(hub.getLedger().open(LedgerParty.held(account, PlayerJourneyTest.balance(coins))).join().isPosted());

        // Servers heartbeat on a timer that keeps going whether or not NATS is there
        final ScheduledExecutorService ticks = Executors.newSingleThreadScheduledExecutor();
        ticks.scheduleAtFixedRate(hub.getHandoff()::heartbeat, 0, Network.LEASE.toMillis() / 4, TimeUnit.MILLISECONDS);
        try {
            network.stopNats();
            Thread.sleep(Network.LEASE.multipliedBy(2).toMillis());

            assertEquals(Set.of(id), hub.getHandoff().owned());
            player.setLevel(4);
            assertTrue(hub.getHandoff().save(player).join(), "saving needs only Postgres");
            final Movement spend = new Movement(LedgerParty.held(account, PlayerJourneyTest.balance(coins)),
                    LedgerParty.system(LedgerAccounts.SHOP), 5, "spend");
            assertEquals(LedgerStatus.UNAVAILABLE, hub.getLedger().post(List.of(spend), null)
                    .get(10, TimeUnit.SECONDS).getStatus());
            assertTrue(hub.getHosts().hostFor("camp", 1, true, hub.getName()).get(10, TimeUnit.SECONDS).isEmpty(),
                    "placement answered with NATS down");
            final CompletableFuture<Boolean> login = hub.getHandoff().arrive(UUID.randomUUID(), Duration.ofSeconds(1));
            assertFalse(login.exceptionally(unreachable -> false).get(10, TimeUnit.SECONDS),
                    "a new player was let in without the sessions service");

            network.startNats();
            network.awaitReconnected();

            assertFalse(other.getHandoff().arrive(id, Duration.ofMillis(500)).join(),
                    "another server took a player who never left " + hub.getName());
            player.setLevel(5);
            assertTrue(hub.getHandoff().save(player).join(), "the server lost its player to the outage");
            assertEquals(5, network.getFixture().getStore().load(id).join().orElseThrow().getLevel());
            assertTrue(hub.getLedger().post(List.of(spend), null).join().isPosted());
            assertEquals(5, coins.get());
        } finally {
            ticks.shutdownNow();
        }
    }
}
