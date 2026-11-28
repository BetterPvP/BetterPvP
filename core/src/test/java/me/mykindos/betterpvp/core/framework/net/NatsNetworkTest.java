package me.mykindos.betterpvp.core.framework.net;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Nats;
import me.mykindos.betterpvp.network.protocol.EnvelopeCodec;
import me.mykindos.betterpvp.network.protocol.NetworkEnvelope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The NATS transports against a real NATS server with JetStream, several servers to a test. Skipped when Docker is
 * not running.
 */
@DisplayName("Servers sharing a real NATS server")
class NatsNetworkTest {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final AtomicInteger NETWORKS = new AtomicInteger();
    private static final ServerRoom ROOMY = new ServerRoom("Clans-1", 0, 100);

    private static GenericContainer<?> nats;
    private static String url;

    private final EnvelopeCodec codec = new EnvelopeCodec();
    private final List<Connection> connections = new ArrayList<>();
    private final List<NatsMessageBus> buses = new ArrayList<>();
    private final List<NatsSiteDirectory> directories = new ArrayList<>();

    /** Each test is its own network, so subjects and buckets never leak between tests. */
    private final String prefix = "test" + NETWORKS.incrementAndGet();

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
        buses.forEach(NatsMessageBus::close);
        directories.forEach(NatsSiteDirectory::close);
        for (Connection connection : connections) {
            connection.close();
        }
    }

    @Test
    @DisplayName("a publish reaches every server, the sender's own included")
    void publishReachesEveryServer() throws Exception {
        final NatsMessageBus first = bus("Clans-1");
        final NatsMessageBus second = bus("Clans-2");
        final CountDownLatch received = new CountDownLatch(2);
        final List<String> origins = new CopyOnWriteArrayList<>();
        for (NatsMessageBus bus : List.of(first, second)) {
            bus.subscribe("chat.clan", message -> {
                origins.add(message.getOrigin());
                received.countDown();
            });
        }
        flush();

        first.publish(BusMessage.of("chat.clan", Map.of("text", "hello")));

        assertTrue(received.await(WAIT.toMillis(), TimeUnit.MILLISECONDS));
        assertEquals(List.of("Clans-1", "Clans-1"), origins);
    }

    @Test
    @DisplayName("a request collects an answer from every server that has one")
    void requestCollectsEveryAnswer() throws Exception {
        final NatsMessageBus asker = bus("Clans-1");
        for (String name : List.of("Clans-2", "Clans-3")) {
            bus(name).answer("player.find", request -> Optional.of(request.replyWith(Map.of("server", name))));
        }
        bus("Clans-4").answer("player.find", request -> Optional.empty());
        flush();

        final List<BusMessage> replies = asker.request(BusMessage.of("player.find", "name", "Tester"),
                Duration.ofMillis(500)).get(WAIT.toMillis(), TimeUnit.MILLISECONDS);

        assertEquals(2, replies.size());
        assertEquals(List.of("Clans-2", "Clans-3"), replies.stream().map(BusMessage::getOrigin).sorted().toList());
    }

    @Test
    @DisplayName("a service with no core speaks to a server with plain NATS requests and replies")
    void aServiceSpeaksTheWireFormat() throws Exception {
        final NatsMessageBus server = bus("Clans-1");
        server.answer("ledger.ping", request -> Optional.of(request.replyWith(Map.of("pong", "yes"))));

        final Connection service = connect();
        final Dispatcher answering = service.createDispatcher((Message message) -> {
            final NetworkEnvelope reply = new NetworkEnvelope("ledger.balance", "ledger", null, true,
                    Map.of("coins", "120"));
            service.publish(message.getReplyTo(), codec.encode(reply));
        });
        answering.subscribe(prefix + ".bus.ledger.balance");
        flush();

        final Message pong = service.request(prefix + ".bus.ledger.ping",
                codec.encode(new NetworkEnvelope("ledger.ping", "ledger", null, false, Map.of())), WAIT);
        assertNotNull(pong, "the server should have answered the service");
        final NetworkEnvelope answer = codec.decode(pong.getData());
        assertTrue(answer.isReply());
        assertEquals("Clans-1", answer.getOrigin());
        assertEquals("yes", answer.getPayload().get("pong"));

        final List<BusMessage> balance = server.request(BusMessage.of("ledger.balance", "player", "Tester"),
                Duration.ofMillis(500)).get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        assertEquals(1, balance.size());
        assertEquals("120", balance.get(0).getPayload().get("coins"));
    }

    @Test
    @DisplayName("an instance one server advertises is found by another")
    void advertisedInstancesAreFound() throws Exception {
        final NatsSiteDirectory holder = directory("Clans-1");
        final NatsSiteDirectory other = directory("Clans-2");
        final RemoteInstance instance = instance("camp:north", 7, "Clans-1", 3);

        holder.publish(List.of(instance), ROOMY);

        eventually(() -> !other.lookup("camp:north", 7).join().isEmpty());
        final RemoteInstance found = other.lookup("camp:north", 7).join().get(0);
        assertEquals(instance.getId(), found.getId());
        assertEquals("Clans-1", found.getServer());
        assertEquals(3, found.getOccupants());
        assertTrue(other.lookup("camp:north", 8).join().isEmpty(), "another owner's site is somewhere else");
    }

    @Test
    @DisplayName("the room a server reports is read by another, and a server that never reported has none")
    void reportedRoomIsRead() throws Exception {
        final NatsSiteDirectory camp = directory("Camp-1");
        final NatsSiteDirectory hub = directory("Hub-1");

        camp.publish(List.of(), new ServerRoom("Camp-1", 70, 72));

        eventually(() -> hub.room("Camp-1").join().isPresent());
        final ServerRoom room = hub.room("Camp-1").join().get();
        assertEquals(70, room.getOnline());
        assertEquals(72, room.getLimit());
        assertTrue(room.fits(2));
        assertFalse(room.fits(3), "a party that would not all fit is turned away");
        assertTrue(hub.room("Camp-2").join().isEmpty());
    }

    @Test
    @DisplayName("two servers taking the last seat at once: exactly one gets it")
    void lastSeatGoesToOne() throws Exception {
        final NatsSiteDirectory first = directory("Clans-1");
        final NatsSiteDirectory second = directory("Clans-2");

        for (int round = 0; round < 10; round++) {
            final RemoteInstance instance = instance("dungeon", round, "Clans-1", 3);
            first.publish(List.of(instance), ROOMY);
            eventually(() -> !second.lookup("dungeon", instance.getOwnerId()).join().isEmpty());

            final CompletableFuture<Boolean> one = first.reserve(instance.getId(), 1, 4);
            final CompletableFuture<Boolean> two = second.reserve(instance.getId(), 1, 4);

            assertTrue(one.join() ^ two.join(), "round " + round + ": one and only one server takes the last seat");
            assertEquals(4, second.lookup("dungeon", instance.getOwnerId()).join().get(0).getOccupants());
        }
    }

    @Test
    @DisplayName("seats given back can be taken again")
    void releasedSeatsAreFree() throws Exception {
        final NatsSiteDirectory directory = directory("Clans-1");
        final RemoteInstance instance = instance("dungeon", 1, "Clans-1", 0);
        directory.publish(List.of(instance), ROOMY);
        eventually(() -> !directory.lookup("dungeon", 1).join().isEmpty());

        assertTrue(directory.reserve(instance.getId(), 2, 2).join());
        assertFalse(directory.reserve(instance.getId(), 1, 2).join());

        directory.release(instance.getId(), 2);

        eventually(() -> directory.lookup("dungeon", 1).join().get(0).getOccupants() == 0);
        assertTrue(directory.reserve(instance.getId(), 1, 2).join());
    }

    @Test
    @DisplayName("an owned site keeps the first host that claimed it")
    void stickyHostKeepsTheFirstClaim() throws Exception {
        final NatsSiteDirectory first = directory("Clans-1");
        final NatsSiteDirectory second = directory("Clans-2");

        assertEquals("Clans-1", first.stickyHost("camp", 42, "Clans-1").join());
        assertEquals("Clans-1", second.stickyHost("camp", 42, "Clans-2").join());
        assertEquals("Clans-2", second.stickyHost("camp", 43, "Clans-2").join());
    }

    @Test
    @DisplayName("an arrival is handed to one claimer only")
    void arrivalIsClaimedOnce() throws Exception {
        final NatsSiteDirectory sender = directory("Clans-1");
        final NatsSiteDirectory first = directory("Clans-2");
        final NatsSiteDirectory second = directory("Clans-3");
        final UUID player = UUID.randomUUID();
        final RemoteInstance instance = instance("camp", 42, "Clans-2", 0);

        sender.expect(player, new Arrival(instance, "dock"));
        eventually(() -> {
            try {
                return connections.get(0).keyValue(prefix + "-arrivals").get(player.toString()) != null;
            } catch (Exception exception) {
                return false;
            }
        });

        final CompletableFuture<Optional<Arrival>> one = first.claimArrival(player);
        final CompletableFuture<Optional<Arrival>> two = second.claimArrival(player);

        final List<Arrival> claimed = new ArrayList<>();
        one.join().ifPresent(claimed::add);
        two.join().ifPresent(claimed::add);
        assertEquals(1, claimed.size());
        assertEquals(instance.getId(), claimed.get(0).getInstance().getId());
        assertEquals("dock", claimed.get(0).getLanding());
        assertTrue(first.claimArrival(player).join().isEmpty(), "reading it consumed it");
    }

    @Test
    @DisplayName("servers are listed emptiest first")
    void serversByLoadEmptiestFirst() throws Exception {
        final NatsSiteDirectory busy = directory("Clans-1");
        final NatsSiteDirectory quiet = directory("Clans-2");

        busy.publish(List.of(instance("camp", 1, "Clans-1", 9)), ROOMY);
        quiet.publish(List.of(instance("camp", 2, "Clans-2", 1)), ROOMY);

        eventually(() -> busy.serversByLoad().join().size() == 2);
        assertEquals(List.of("Clans-2", "Clans-1"), busy.serversByLoad().join());
    }

    private NatsMessageBus bus(String name) throws Exception {
        final NatsMessageBus bus = new NatsMessageBus(connect(), prefix, () -> name);
        bus.start();
        buses.add(bus);
        return bus;
    }

    private NatsSiteDirectory directory(String name) throws Exception {
        final NatsSiteDirectory directory = NatsSiteDirectory.open(connect(), prefix, 1, () -> name);
        assertNotNull(directory, "JetStream is on in the test server");
        directories.add(directory);
        return directory;
    }

    private Connection connect() throws Exception {
        final Connection connection = Nats.connect(url);
        connections.add(connection);
        return connection;
    }

    /** Waits until every connection's subscriptions have reached the server. */
    private void flush() throws Exception {
        for (Connection connection : connections) {
            connection.flush(WAIT);
        }
    }

    private RemoteInstance instance(String site, long owner, String server, int occupants) {
        return new RemoteInstance(UUID.randomUUID(), site, owner, server, "world_" + owner, true, occupants);
    }

    private void eventually(BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        fail("the condition did not hold within " + WAIT);
    }
}
