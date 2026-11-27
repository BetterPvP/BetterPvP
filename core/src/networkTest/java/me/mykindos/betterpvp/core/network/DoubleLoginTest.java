package me.mykindos.betterpvp.core.network;

import me.mykindos.betterpvp.sessions.ProxySessions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("One account on two proxies")
class DoubleLoginTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

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
    @DisplayName("is refused on the second proxy until the first lets go")
    void secondProxyIsRefused() throws Exception {
        final ProxySessions first = network.proxy("proxy-1");
        final ProxySessions second = network.proxy("proxy-2");
        final UUID account = UUID.randomUUID();

        assertTrue(first.login(account, TIMEOUT).join());
        assertFalse(second.login(account, TIMEOUT).join());
        assertTrue(first.login(account, TIMEOUT).join(), "the proxy that holds the account was refused it again");

        first.logout(account, TIMEOUT).join();
        assertTrue(second.login(account, TIMEOUT).join());
        assertFalse(first.login(account, TIMEOUT).join());
    }

    @Test
    @DisplayName("is refused everywhere but one when two proxies log it in at once")
    void simultaneousLoginsAgree() throws Exception {
        final ProxySessions first = network.proxy("proxy-3");
        final ProxySessions second = network.proxy("proxy-4");
        for (int attempt = 0; attempt < 20; attempt++) {
            final UUID account = UUID.randomUUID();
            final CompletableFuture<Boolean> one = first.login(account, TIMEOUT);
            final CompletableFuture<Boolean> two = second.login(account, TIMEOUT);
            assertEquals(1, (one.join() ? 1 : 0) + (two.join() ? 1 : 0), "attempt " + attempt);
        }
    }

    @Test
    @DisplayName("frees up once the proxy holding it stops heartbeating")
    void deadProxyLetsGo() throws Exception {
        final ProxySessions dead = network.proxy("proxy-5");
        final ProxySessions alive = network.proxy("proxy-6");
        final UUID account = UUID.randomUUID();

        assertTrue(dead.login(account, TIMEOUT).join());
        assertFalse(alive.login(account, TIMEOUT).join());

        // The live proxy heartbeats as every proxy does, the dead one has gone quiet
        final long until = System.currentTimeMillis() + Network.LEASE.plusMillis(500).toMillis();
        while (System.currentTimeMillis() < until) {
            alive.heartbeat();
            Thread.sleep(200);
        }
        assertTrue(alive.login(account, TIMEOUT).join(), "a dead proxy kept the account");
    }

    @Test
    @DisplayName("lands on one server even if both proxies send the player somewhere")
    void oneServerOwnsThePlayer() throws Exception {
        final FakeServer hub = network.server("hub-a");
        final FakeServer other = network.server("hub-b");
        final UUID player = UUID.randomUUID();

        final CompletableFuture<Boolean> one = hub.getHandoff().arrive(player, Duration.ofMillis(500));
        final CompletableFuture<Boolean> two = other.getHandoff().arrive(player, Duration.ofMillis(500));
        assertEquals(1, (one.join() ? 1 : 0) + (two.join() ? 1 : 0));
    }
}
