package me.mykindos.betterpvp.proxy.network;

import io.nats.client.Connection;
import io.nats.client.Nats;
import me.mykindos.betterpvp.network.protocol.EnvelopeCodec;
import me.mykindos.betterpvp.network.protocol.NetworkEnvelope;
import me.mykindos.betterpvp.network.protocol.PlacementMessages;
import me.mykindos.betterpvp.network.protocol.PlayerRoster;
import me.mykindos.betterpvp.network.protocol.Subjects;
import me.mykindos.betterpvp.sessions.ProxySessions;
import me.mykindos.betterpvp.sessions.SessionCalls;
import me.mykindos.betterpvp.sessions.service.LeaseBook;
import me.mykindos.betterpvp.sessions.service.SessionEndpoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Two proxies sharing a real NATS server, the way two Velocity pods behind one load balancer would. */
@DisplayName("Two proxies on one network")
class TwoProxiesTest {

    private static final Logger LOG = NOPLogger.NOP_LOGGER;
    private static final AtomicInteger NETWORKS = new AtomicInteger();

    private static GenericContainer<?> nats;
    private static String url;

    private final List<Connection> connections = new ArrayList<>();
    private final String prefix = "proxies" + NETWORKS.incrementAndGet();
    private SessionEndpoint sessions;

    /** One proxy's network side, with nothing of Velocity. */
    private class Proxy {
        final ProxyBus bus;
        final PlayerPresence presence;
        final NetworkLogins logins;

        Proxy(String name, Duration staleAfter) throws Exception {
            final Connection connection = connect();
            bus = new ProxyBus(connection, prefix, name, LOG);
            presence = new PlayerPresence(bus, new PlayerRoster(staleAfter, System::currentTimeMillis));
            presence.start();
            logins = new NetworkLogins(new ServiceSessions(new ProxySessions(new SessionCalls(connection, prefix, name))),
                    presence, LOG);
            bus.flush(Duration.ofSeconds(5));
        }

        NetworkLogins.Verdict join(UUID player) {
            return logins.login(player, "Player").join();
        }
    }

    @BeforeAll
    static void startNats() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not running");
        nats = new GenericContainer<>(DockerImageName.parse("nats:2.11-alpine"))
                .withCommand("-js")
                .withExposedPorts(4222)
                .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1));
        nats.start();
        url = "nats://" + nats.getHost() + ":" + nats.getMappedPort(4222);
    }

    @AfterAll
    static void stopNats() {
        if (nats != null) {
            nats.stop();
        }
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (sessions != null) {
            sessions.close();
        }
        for (Connection connection : connections) {
            connection.close();
        }
    }

    @Test
    @DisplayName("with two proxies, a player is never online twice")
    void neverOnlineTwice() throws Exception {
        startSessions(Duration.ofSeconds(15));
        final Proxy first = new Proxy("proxy-a", Duration.ofSeconds(15));
        final Proxy second = new Proxy("proxy-b", Duration.ofSeconds(15));
        final UUID player = UUID.randomUUID();

        assertEquals(NetworkLogins.Verdict.ACCEPTED, first.join(player));
        eventually(() -> second.presence.elsewhere(player).isPresent());
        assertEquals(NetworkLogins.Verdict.ONLINE_ELSEWHERE, second.join(player),
                "the network player list keeps them off the second proxy");

        first.logins.logout(player).join();
        eventually(() -> second.presence.elsewhere(player).isEmpty());
        assertEquals(NetworkLogins.Verdict.ACCEPTED, second.join(player));
    }

    @Test
    @DisplayName("two proxies letting the same player in at the same moment let them in once")
    void simultaneousLoginsLandOnce() throws Exception {
        startSessions(Duration.ofSeconds(15));
        final Proxy first = new Proxy("proxy-a", Duration.ofSeconds(15));
        final Proxy second = new Proxy("proxy-b", Duration.ofSeconds(15));

        for (int round = 0; round < 20; round++) {
            final UUID player = UUID.randomUUID();
            final CountDownLatch go = new CountDownLatch(1);
            final CompletableFuture<NetworkLogins.Verdict> one = CompletableFuture.supplyAsync(() -> {
                await(go);
                return first.join(player);
            });
            final CompletableFuture<NetworkLogins.Verdict> two = CompletableFuture.supplyAsync(() -> {
                await(go);
                return second.join(player);
            });
            go.countDown();

            final long accepted = List.of(one.join(), two.join()).stream()
                    .filter(NetworkLogins.Verdict.ACCEPTED::equals)
                    .count();
            assertEquals(1, accepted, "round " + round);
        }
    }

    @Test
    @DisplayName("restarting one proxy reconnects its players through the other")
    void restartHandsPlayersOver() throws Exception {
        startSessions(Duration.ofSeconds(15));
        final Proxy first = new Proxy("proxy-a", Duration.ofSeconds(15));
        final Proxy second = new Proxy("proxy-b", Duration.ofSeconds(15));
        final UUID alex = UUID.randomUUID();
        final UUID sam = UUID.randomUUID();
        first.join(alex);
        first.join(sam);
        eventually(() -> second.presence.elsewhere(alex).isPresent() && second.presence.elsewhere(sam).isPresent());

        first.logins.shutdown();
        first.bus.flush(Duration.ofSeconds(5));

        eventually(() -> second.presence.elsewhere(alex).isEmpty() && second.presence.elsewhere(sam).isEmpty());
        assertEquals(NetworkLogins.Verdict.ACCEPTED, second.join(alex));
        assertEquals(NetworkLogins.Verdict.ACCEPTED, second.join(sam));
    }

    @Test
    @DisplayName("a proxy that dies without stopping cleanly stops holding its players once it goes quiet")
    void crashedProxyLetsGo() throws Exception {
        startSessions(Duration.ofSeconds(1));
        final Proxy first = new Proxy("proxy-a", Duration.ofSeconds(15));
        final Proxy second = new Proxy("proxy-b", Duration.ofSeconds(1));
        final UUID player = UUID.randomUUID();
        first.join(player);
        eventually(() -> second.presence.elsewhere(player).isPresent());

        // proxy-a never says goodbye and stops sending its roster and heartbeat.
        Thread.sleep(1_500);

        assertEquals(NetworkLogins.Verdict.ACCEPTED, second.join(player));
    }

    @Test
    @DisplayName("when sessions cannot be asked, nobody is let in")
    void unreachableSessionsRefuse() throws Exception {
        // No sessions service is running, so the login is never answered.
        final Proxy proxy = new Proxy("proxy-a", Duration.ofSeconds(15));

        assertEquals(NetworkLogins.Verdict.UNAVAILABLE, proxy.join(UUID.randomUUID()));
        assertTrue(proxy.presence.here().isEmpty(), "a refused player is never announced");
    }

    @Test
    @DisplayName("a joining player is sent to the hub Placement picks, and nowhere when Placement is silent")
    void hubComesFromPlacement() throws Exception {
        final Proxy proxy = new Proxy("proxy-a", Duration.ofSeconds(15));
        final HubRouter router = new HubRouter(proxy.bus, "hub", LOG);

        assertEquals(Optional.empty(), router.hub().join());

        final EnvelopeCodec codec = new EnvelopeCodec();
        final Connection placement = connect();
        final List<Map<String, String>> asked = new ArrayList<>();
        placement.createDispatcher(message -> {
            try {
                final NetworkEnvelope request = codec.decode(message.getData());
                asked.add(request.getPayload());
                placement.publish(message.getReplyTo(), codec.encode(new NetworkEnvelope(PlacementMessages.PLACE,
                        "placement", request.getCorrelationId(), true, Map.of(PlacementMessages.SERVER, "hub-3",
                        PlacementMessages.OUTCOME, PlacementMessages.PLACED))));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }).subscribe(Subjects.bus(prefix, PlacementMessages.PLACE), "placement");
        placement.flush(Duration.ofSeconds(5));

        assertEquals(Optional.of("hub-3"), router.hub().join());
        assertEquals(Map.of(PlacementMessages.SITE, "hub", PlacementMessages.OWNER, "0",
                PlacementMessages.OWNED, "false", PlacementMessages.ASKER, "proxy-a"), asked.get(0));
    }

    private void startSessions(Duration proxyTimeout) throws Exception {
        final Connection connection = connect();
        sessions = new SessionEndpoint(connection, prefix,
                new LeaseBook(10_000, proxyTimeout.toMillis(), System::currentTimeMillis),
                Executors.newScheduledThreadPool(2), 10_000);
        sessions.start();
        connection.flush(Duration.ofSeconds(5));
    }

    private Connection connect() throws Exception {
        final Connection connection = Nats.connect(url);
        connections.add(connection);
        return connection;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void eventually(BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        fail("the condition did not hold within 5s");
    }
}
