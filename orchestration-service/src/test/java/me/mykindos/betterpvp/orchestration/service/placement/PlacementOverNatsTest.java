package me.mykindos.betterpvp.orchestration.service.placement;

import io.nats.client.Connection;
import io.nats.client.KeyValue;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.StorageType;
import me.mykindos.betterpvp.network.protocol.DirectoryKeys;
import me.mykindos.betterpvp.network.protocol.EnvelopeCodec;
import me.mykindos.betterpvp.network.protocol.NetworkEnvelope;
import me.mykindos.betterpvp.network.protocol.PlacementMessages;
import me.mykindos.betterpvp.network.protocol.Subjects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Placement against a real NATS server with JetStream, answering the way servers ask. Skipped without Docker. */
@DisplayName("Placement over NATS")
class PlacementOverNatsTest {

    private static final AtomicInteger NETWORKS = new AtomicInteger();

    private static GenericContainer<?> nats;
    private static String url;

    private final EnvelopeCodec codec = new EnvelopeCodec();
    private final List<Connection> connections = new ArrayList<>();
    private final List<PlacementResponder> responders = new ArrayList<>();
    private final List<FakeKubernetes> clusters = new ArrayList<>();
    private final String prefix = "placement" + NETWORKS.incrementAndGet();

    /** A pool whose servers a test can swap out between asks. */
    private static class SwitchablePool implements ServerPool {
        volatile List<Slots> servers;

        SwitchablePool(String server) {
            // Room for every camp a test opens, so only the lease decides where each goes.
            this.servers = List.of(new Slots(server, 1_000));
        }

        @Override
        public List<Slots> servers(String role) {
            return servers;
        }

        @Override
        public Optional<String> allocate(String role) {
            return Optional.empty();
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
        responders.forEach(PlacementResponder::close);
        clusters.forEach(FakeKubernetes::close);
        for (Connection connection : connections) {
            connection.close();
        }
    }

    @Test
    @DisplayName("two simultaneous opens of the same camp land on one server")
    void simultaneousOpensAgree() throws Exception {
        // Two Placement replicas that would each pick a different server if left alone.
        final Placer first = placer(new SwitchablePool("camp-a"), Duration.ofSeconds(60));
        final Placer second = placer(new SwitchablePool("camp-b"), Duration.ofSeconds(60));
        respond(first);
        respond(second);

        for (int camp = 0; camp < 20; camp++) {
            final long owner = camp;
            final CountDownLatch go = new CountDownLatch(1);
            final CompletableFuture<PlacementDecision> one = CompletableFuture.supplyAsync(() -> {
                await(go);
                return first.place("camp", owner, true);
            });
            final CompletableFuture<PlacementDecision> two = CompletableFuture.supplyAsync(() -> {
                await(go);
                return second.place("camp", owner, true);
            });
            go.countDown();

            assertEquals(one.join().getServer(), two.join().getServer(), "camp " + camp);
        }

        // And the same through the bus, the way two servers would ask at once.
        final Connection serverOne = connect();
        final Connection serverTwo = connect();
        for (int camp = 100; camp < 110; camp++) {
            final long owner = camp;
            final CompletableFuture<Map<String, String>> one = CompletableFuture.supplyAsync(() -> ask(serverOne, owner));
            final CompletableFuture<Map<String, String>> two = CompletableFuture.supplyAsync(() -> ask(serverTwo, owner));

            assertEquals(one.join().get(PlacementMessages.SERVER), two.join().get(PlacementMessages.SERVER),
                    "camp " + camp + " over the bus");
        }
    }

    @Test
    @DisplayName("with no free slots, Placement allocates a new camp server and opens the camp there")
    void allocatesWhenFull() throws Exception {
        final FakeKubernetes kubernetes = new FakeKubernetes();
        clusters.add(kubernetes);
        kubernetes.addServer("camp-full", "Allocated", 10, 10);
        kubernetes.addServer("camp-spare", "Ready", 0, 10);
        final AgonesServerPool pool = new AgonesServerPool(HttpClient.newHttpClient(), kubernetes.url(), "games",
                () -> null, Map.of("camp", new AgonesServerPool.Fleet("camp", "camp-slots")));
        respond(placer(pool, Duration.ofSeconds(60)));

        final Connection server = connect();
        final Map<String, String> answer = ask(server, 42);

        assertEquals(PlacementMessages.ALLOCATED, answer.get(PlacementMessages.OUTCOME));
        assertEquals("camp-spare", answer.get(PlacementMessages.SERVER));
        assertEquals(1, kubernetes.allocationBodies.size());

        final KeyValueEntry lease = server.keyValue(DirectoryKeys.leases(prefix)).get(DirectoryKeys.site("camp", 42));
        assertNotNull(lease, "the camp is leased to the server it was sent to");
        assertEquals("camp-spare", codec.decodeFields(lease.getValue()).get("server"));

        final Map<String, String> again = ask(server, 42);
        assertEquals(PlacementMessages.HOSTED, again.get(PlacementMessages.OUTCOME));
        assertEquals("camp-spare", again.get(PlacementMessages.SERVER));
        assertEquals(1, kubernetes.allocationBodies.size(), "a hosted camp allocates nothing more");
    }

    @Test
    @DisplayName("a lease that stops being renewed expires and the camp can open elsewhere")
    void unrenewedLeaseExpires() throws Exception {
        final SwitchablePool pool = new SwitchablePool("camp-a");
        respond(placer(pool, Duration.ofSeconds(1)));
        final Connection server = connect();

        assertEquals("camp-a", ask(server, 7).get(PlacementMessages.SERVER));
        assertEquals("camp-a", ask(server, 8).get(PlacementMessages.SERVER));

        // camp-a keeps advertising camp 8, which renews it, and goes quiet about camp 7.
        advertise(server, "camp", 8, "camp-a");
        pool.servers = List.of(new ServerPool.Slots("camp-b", 5));
        Thread.sleep(1_500);

        final Map<String, String> moved = ask(server, 7);
        assertEquals(PlacementMessages.PLACED, moved.get(PlacementMessages.OUTCOME));
        assertEquals("camp-b", moved.get(PlacementMessages.SERVER));
        assertEquals("camp-a", ask(server, 8).get(PlacementMessages.SERVER), "a renewed lease stays put");
    }

    private Placer placer(ServerPool pool, Duration ttl) throws Exception {
        return new Placer(NatsLeaseStore.open(connect(), prefix, 1), pool, site -> "camp", ttl,
                System::currentTimeMillis);
    }

    private void respond(Placer placer) throws Exception {
        final Connection connection = connect();
        final PlacementResponder responder = new PlacementResponder(connection, placer, "placement");
        responder.start(prefix);
        connection.flush(Duration.ofSeconds(10));
        responders.add(responder);
    }

    /** Asks Placement for camp {@code owner} exactly as a server's bus would. */
    private Map<String, String> ask(Connection connection, long owner) {
        try {
            final NetworkEnvelope request = new NetworkEnvelope(PlacementMessages.PLACE, "Clans-1",
                    UUID.randomUUID().toString(), false, Map.of(PlacementMessages.SITE, "camp",
                    PlacementMessages.OWNER, String.valueOf(owner), PlacementMessages.OWNED, "true",
                    PlacementMessages.ASKER, "Clans-1"));
            final Message reply = connection.request(Subjects.bus(prefix, PlacementMessages.PLACE),
                    codec.encode(request), Duration.ofSeconds(10));
            assertNotNull(reply, "Placement should answer");
            return codec.decode(reply.getData()).getPayload();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Writes the instance entry a server's heartbeat would. */
    private void advertise(Connection connection, String site, long owner, String server) throws Exception {
        final String bucket = DirectoryKeys.instances(prefix);
        connection.keyValueManagement().create(KeyValueConfiguration.builder()
                .name(bucket).maxHistoryPerKey(1).storageType(StorageType.Memory).build());
        final KeyValue instances = connection.keyValue(bucket);
        final UUID id = UUID.randomUUID();
        instances.put(DirectoryKeys.instance(site, owner, id), codec.encodeFields(Map.of(
                "id", id.toString(), "site", site, "owner", String.valueOf(owner), "server", server,
                "world", "camps/" + owner, "ready", "true", "occupants", "0")));
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
}
