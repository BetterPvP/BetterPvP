package me.mykindos.betterpvp.core.playerstate;

import io.nats.client.Connection;
import io.nats.client.Nats;
import me.mykindos.betterpvp.core.playerstate.session.NatsPlayerLeases;
import me.mykindos.betterpvp.core.playerstate.session.PlayerHandoff;
import me.mykindos.betterpvp.sessions.SessionCalls;
import me.mykindos.betterpvp.sessions.service.LeaseBook;
import me.mykindos.betterpvp.sessions.service.SessionEndpoint;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two servers handing a player over through a real sessions service, NATS and Postgres. Skipped when Docker is not
 * running.
 */
class PlayerHandoffTest {

    private static final AtomicInteger NETWORKS = new AtomicInteger();

    private static GenericContainer<?> nats;
    private static String url;

    private final List<Connection> connections = new ArrayList<>();
    private ServerMock server;
    private PlayerStateFixture fixture;
    private SessionEndpoint endpoint;
    private Connection sourceConnection;
    private PlayerHandoff source;
    private PlayerHandoff target;

    @BeforeAll
    static void startServices() {
        PlayerStateFixture.startPostgres();
        nats = new GenericContainer<>(DockerImageName.parse("nats:2.11-alpine"))
                .withExposedPorts(4222)
                .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1));
        nats.start();
        url = "nats://" + nats.getHost() + ":" + nats.getMappedPort(4222);
    }

    @AfterAll
    static void stopServices() {
        if (nats != null) {
            nats.stop();
        }
        PlayerStateFixture.stopPostgres();
    }

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        fixture = new PlayerStateFixture();
        final String prefix = "handoff" + NETWORKS.incrementAndGet();

        final Connection serviceConnection = connect();
        endpoint = new SessionEndpoint(serviceConnection, prefix,
                new LeaseBook(1000, 1000, System::currentTimeMillis),
                Executors.newScheduledThreadPool(2), 10_000);
        endpoint.start();
        serviceConnection.flush(Duration.ofSeconds(2));

        sourceConnection = connect();
        source = handoff(sourceConnection, prefix, "Source");
        target = handoff(connect(), prefix, "Target");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        endpoint.close();
        for (Connection connection : connections) {
            connection.close();
        }
        MockBukkit.unmock();
    }

    @Test
    void movingAPlayerKeepsEveryItemWithNoDuplication() throws Exception {
        final UUID id = UUID.randomUUID();
        assertTrue(source.arrive(id, Duration.ofSeconds(2)).join());
        final PlayerMock onSource = join(source, id);
        final ItemStack sword = fixture.blade(5);
        final ItemStack stored = fixture.blade(9);
        final ItemStack dirt = new ItemStack(Material.DIRT, 12);
        onSource.getInventory().setItem(0, sword);
        onSource.getInventory().setItem(3, dirt);
        onSource.getEnderChest().setItem(7, stored);

        final CompletableFuture<Boolean> targetClaim = target.arrive(id, Duration.ofSeconds(5));
        assertThrows(TimeoutException.class, () -> targetClaim.get(300, TimeUnit.MILLISECONDS),
                "the target took the player while the source still owned them");

        source.leave(onSource).join();
        onSource.disconnect();
        assertTrue(targetClaim.get(5, TimeUnit.SECONDS));
        final PlayerMock onTarget = join(target, id);

        fixture.assertSameItem(sword, onTarget.getInventory().getItem(0));
        fixture.assertSameItem(dirt, onTarget.getInventory().getItem(3));
        fixture.assertSameItem(stored, onTarget.getEnderChest().getItem(7));
        assertEquals(Set.of(), source.owned());
        assertEquals(Set.of(id), target.owned());
        assertFalse(source.save(onSource).join(), "the source saved a player it had handed over");
    }

    @Test
    void killingTheSourceMidMoveLosesNothingSavedAndDuplicatesNothing() throws Exception {
        final UUID id = UUID.randomUUID();
        assertTrue(source.arrive(id, Duration.ofSeconds(2)).join());
        final PlayerMock onSource = join(source, id);
        final ItemStack saved = fixture.blade(1);
        onSource.getInventory().setItem(0, saved);
        assertTrue(source.save(onSource).join());

        // Changed after the last save, then the source dies without releasing
        onSource.getInventory().setItem(0, fixture.blade(2));
        onSource.getInventory().setItem(1, fixture.blade(3));
        sourceConnection.close();

        assertTrue(target.arrive(id, Duration.ofSeconds(5)).join(), "the dead source's lease never expired");
        onSource.disconnect();
        final PlayerMock onTarget = join(target, id);
        fixture.assertSameItem(saved, onTarget.getInventory().getItem(0));
        fixture.assertSameItem(null, onTarget.getInventory().getItem(1));

        assertFalse(source.save(onSource).join(), "the dead source overwrote the target's state");
        final PlayerState kept = fixture.getStore().load(id).join().orElseThrow();
        fixture.assertSameItem(saved, kept.getInventory().get(0));
        assertFalse(kept.getInventory().containsKey(1));
    }

    @Test
    void aServerCannotTakeAPlayerAnotherServerOwns() {
        final UUID id = UUID.randomUUID();
        assertTrue(source.arrive(id, Duration.ofSeconds(2)).join());
        source.heartbeat();
        assertFalse(target.arrive(id, Duration.ofMillis(200)).join());
        assertEquals(Set.of(), target.owned());
    }

    private PlayerHandoff handoff(Connection connection, String prefix, String name) {
        return new PlayerHandoff(new NatsPlayerLeases(new SessionCalls(connection, prefix, name), Duration.ofSeconds(2)),
                fixture.getStore(), fixture.getCapture());
    }

    private PlayerMock join(PlayerHandoff handoff, UUID id) {
        final PlayerMock player = new PlayerMock(server, "Mover", id);
        server.addPlayer(player);
        handoff.join(player);
        return player;
    }

    private Connection connect() throws Exception {
        final Connection connection = Nats.connect(url);
        connections.add(connection);
        return connection;
    }
}
