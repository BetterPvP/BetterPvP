package me.mykindos.betterpvp.sessions.service;

import io.nats.client.Connection;
import io.nats.client.Nats;
import me.mykindos.betterpvp.sessions.ProxySessions;
import me.mykindos.betterpvp.sessions.SessionCalls;
import me.mykindos.betterpvp.sessions.SessionProtocol;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The service answering over a real NATS server. Skipped when Docker is not running. */
class SessionEndpointTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final AtomicInteger NETWORKS = new AtomicInteger();

    private static GenericContainer<?> nats;
    private static String url;

    private final List<Connection> connections = new ArrayList<>();
    private SessionEndpoint endpoint;
    private String prefix;

    @BeforeAll
    static void startNats() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not running");
        nats = new GenericContainer<>(DockerImageName.parse("nats:2.11-alpine"))
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

    @BeforeEach
    void setUp() throws Exception {
        prefix = "sessions" + NETWORKS.incrementAndGet();
        final Connection connection = connect();
        endpoint = new SessionEndpoint(connection, prefix, new LeaseBook(10_000, 10_000, System::currentTimeMillis),
                Executors.newScheduledThreadPool(2), 10_000);
        endpoint.start();
        connection.flush(TIMEOUT);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        endpoint.close();
        for (Connection connection : connections) {
            connection.close();
        }
    }

    @Test
    void aSecondLoginOfTheSameAccountIsRefused() throws Exception {
        final ProxySessions first = new ProxySessions(new SessionCalls(connect(), prefix, "proxy-1"));
        final ProxySessions second = new ProxySessions(new SessionCalls(connect(), prefix, "proxy-2"));
        final UUID player = UUID.randomUUID();

        assertTrue(first.login(player, TIMEOUT).join());
        assertFalse(second.login(player, TIMEOUT).join());

        first.logout(player, TIMEOUT).join();
        assertTrue(second.login(player, TIMEOUT).join());
    }

    @Test
    void aClaimWaitsForTheRelease() throws Exception {
        final SessionCalls source = new SessionCalls(connect(), prefix, "Source");
        final SessionCalls target = new SessionCalls(connect(), prefix, "Target");
        final UUID player = UUID.randomUUID();

        final Map<String, String> granted = claim(source, player, 0).join();
        assertEquals(SessionProtocol.GRANTED, granted.get(SessionProtocol.RESULT));

        final CompletableFuture<Map<String, String>> waiting = claim(target, player, 5_000);
        Thread.sleep(300);
        assertFalse(waiting.isDone());

        final Map<String, String> released = source.ask(SessionProtocol.RELEASE, Map.of(
                SessionProtocol.PLAYER, player.toString(),
                SessionProtocol.SERVER, "Source",
                SessionProtocol.EPOCH, granted.get(SessionProtocol.EPOCH)), TIMEOUT).join();
        assertEquals(SessionProtocol.RELEASED, released.get(SessionProtocol.RESULT));
        assertEquals(SessionProtocol.GRANTED, waiting.join().get(SessionProtocol.RESULT));
    }

    @Test
    void aClaimThatWaitsTooLongIsToldWhoHoldsThePlayer() throws Exception {
        final SessionCalls source = new SessionCalls(connect(), prefix, "Source");
        final SessionCalls target = new SessionCalls(connect(), prefix, "Target");
        final UUID player = UUID.randomUUID();

        claim(source, player, 0).join();
        final Map<String, String> held = claim(target, player, 200).join();
        assertEquals(SessionProtocol.HELD, held.get(SessionProtocol.RESULT));
        assertEquals("Source", held.get(SessionProtocol.HOLDER));
    }

    private CompletableFuture<Map<String, String>> claim(SessionCalls calls, UUID player, long wait) {
        return calls.ask(SessionProtocol.CLAIM, Map.of(
                SessionProtocol.PLAYER, player.toString(),
                SessionProtocol.SERVER, calls.origin(),
                SessionProtocol.WAIT, String.valueOf(wait)), TIMEOUT.plusMillis(wait));
    }

    private Connection connect() throws Exception {
        final Connection connection = Nats.connect(url);
        connections.add(connection);
        return connection;
    }
}
