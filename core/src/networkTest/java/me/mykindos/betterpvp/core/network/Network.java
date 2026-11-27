package me.mykindos.betterpvp.core.network;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.Nats;
import io.nats.client.Options;
import lombok.Getter;
import me.mykindos.betterpvp.clans.service.ClanService;
import me.mykindos.betterpvp.clans.service.ClanStore;
import me.mykindos.betterpvp.core.playerstate.PlayerStateFixture;
import me.mykindos.betterpvp.ledger.service.LedgerRequests;
import me.mykindos.betterpvp.ledger.service.LedgerServiceApplication;
import me.mykindos.betterpvp.ledger.service.NatsLedgerEndpoint;
import me.mykindos.betterpvp.ledger.service.PostgresLedgerStore;
import me.mykindos.betterpvp.orchestration.service.placement.NatsLeaseStore;
import me.mykindos.betterpvp.orchestration.service.placement.PlacementResponder;
import me.mykindos.betterpvp.orchestration.service.placement.Placer;
import me.mykindos.betterpvp.orchestration.service.placement.ServerPool;
import me.mykindos.betterpvp.sessions.ProxySessions;
import me.mykindos.betterpvp.sessions.SessionCalls;
import me.mykindos.betterpvp.sessions.service.LeaseBook;
import me.mykindos.betterpvp.sessions.service.SessionEndpoint;
import org.jetbrains.annotations.NotNull;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * NATS and Postgres in Docker, with the sessions, placement, ledger and clans services running in this JVM against
 * them, the way they run on the cluster. Both containers keep their host port across a stop and start, so a test can
 * take either down and bring it back while everything stays connected to the same address.
 * <p>
 * Build one after MockBukkit is mocked. Every network gets its own subject prefix.
 */
public final class Network implements AutoCloseable {

    /** How long a server or proxy that stops heartbeating keeps what it holds. */
    public static final Duration LEASE = Duration.ofSeconds(2);

    private static final AtomicInteger NETWORKS = new AtomicInteger();

    @Getter
    private final String prefix = "it" + NETWORKS.incrementAndGet();
    private final int natsPort = freePort();
    private final int postgresPort = freePort();
    private final GenericContainer<?> nats;
    private final GenericContainer<?> postgres;

    @Getter
    private final PGSimpleDataSource dataSource;
    private final HikariDataSource ledgerDatabase;
    private final HikariDataSource clansDatabase;
    @Getter
    private final PlayerStateFixture fixture;

    /** Every server the placement service may choose, by role, with its free slots. */
    private final Map<String, List<ServerPool.Slots>> pool = new ConcurrentHashMap<>();
    private final List<AutoCloseable> running = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();

    public Network() throws Exception {
        final boolean docker = DockerClientFactory.instance().isDockerAvailable();
        // A skip on CI would pass without testing anything
        if (System.getenv("CI") != null) {
            assertTrue(docker, "Docker is not running");
        }
        assumeTrue(docker, "Docker is not running");
        nats = pinned(new GenericContainer<>(DockerImageName.parse("nats:2.11-alpine"))
                .withCommand("-js", "-sd", "/data")
                .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1)), 4222, natsPort);
        postgres = pinned(new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withEnv("POSTGRES_PASSWORD", "test")
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2)), 5432, postgresPort);
        nats.start();
        postgres.start();

        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(jdbcUrl());
        dataSource.setUser("postgres");
        dataSource.setPassword("test");
        dataSource.setConnectTimeout(2);
        PlayerStateFixture.migrate(dataSource);
        createClanTables();
        fixture = new PlayerStateFixture(dataSource);

        try (var connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE ledger");
        }
        ledgerDatabase = LedgerServiceApplication.dataSource(jdbcUrl("ledger"), "postgres", "test", 4);
        LedgerServiceApplication.migrate(ledgerDatabase);
        clansDatabase = clansDataSource();

        startSessions();
        startPlacement();
        // Two ledger replicas share the queue group, as on the cluster, so postings from one server land on either
        startLedger();
        startLedger();
        startClans();
    }

    /** A game server on this network, named as it signs its messages. */
    public @NotNull FakeServer server(@NotNull String name) throws Exception {
        final FakeServer server = new FakeServer(this, name, connect(name));
        running.add(server);
        return server;
    }

    /** The sessions client a proxy named {@code name} uses, on its own connection. */
    public @NotNull ProxySessions proxy(@NotNull String name) throws Exception {
        return new ProxySessions(new SessionCalls(connect(name), prefix, name));
    }

    /** Lets placement put sites of {@code role} on these servers, each with room to spare. */
    public void offer(@NotNull String role, @NotNull String... servers) {
        final List<ServerPool.Slots> slots = new ArrayList<>();
        for (String server : servers) {
            slots.add(new ServerPool.Slots(server, 100));
        }
        pool.put(role, List.copyOf(slots));
    }

    /** Stops NATS without letting it say goodbye, as a crash would. */
    public void stopNats() {
        docker().stopContainerCmd(nats.getContainerId()).withTimeout(0).exec();
    }

    public void startNats() {
        if (!isRunning(nats)) {
            docker().startContainerCmd(nats.getContainerId()).exec();
        }
        await("NATS to come back", () -> accepts(natsPort));
    }

    /** Stops Postgres without letting it say goodbye. Committed work survives, as it would a crash. */
    public void stopPostgres() {
        docker().stopContainerCmd(postgres.getContainerId()).withTimeout(0).exec();
    }

    public void startPostgres() {
        if (!isRunning(postgres)) {
            docker().startContainerCmd(postgres.getContainerId()).exec();
        }
        await("Postgres to take connections", () -> {
            try (var connection = dataSource.getConnection()) {
                return connection.isValid(1);
            } catch (Exception notYet) {
                return false;
            }
        });
    }

    /** Waits until every connection this network opened is talking to NATS again. */
    public void awaitReconnected() {
        await("every client to reconnect to NATS",
                () -> connections.stream().allMatch(connection -> connection.getStatus() == Connection.Status.CONNECTED));
    }

    public static void await(@NotNull String what, @NotNull BooleanSupplier condition) {
        final long deadline = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting for " + what);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted waiting for " + what);
            }
        }
    }

    @Override
    public void close() throws Exception {
        for (AutoCloseable closeable : running.reversed()) {
            closeable.close();
        }
        for (Connection connection : connections) {
            connection.close();
        }
        ledgerDatabase.close();
        clansDatabase.close();
        nats.stop();
        postgres.stop();
    }

    @NotNull Connection connect(@NotNull String name) throws Exception {
        return connect(name, null);
    }

    private @NotNull Connection connect(@NotNull String name, ConnectionListener listener) throws Exception {
        final Options.Builder options = new Options.Builder()
                .server("nats://localhost:" + natsPort)
                .connectionName(name)
                .maxReconnects(-1)
                .reconnectWait(Duration.ofMillis(200))
                .pingInterval(Duration.ofSeconds(1));
        if (listener != null) {
            options.connectionListener(listener);
        }
        final Connection connection = Nats.connect(options.build());
        connections.add(connection);
        return connection;
    }

    private void startSessions() throws Exception {
        final Connection connection = connect("sessions");
        final SessionEndpoint endpoint = new SessionEndpoint(connection, prefix,
                new LeaseBook(LEASE.toMillis(), LEASE.toMillis(), System::currentTimeMillis),
                Executors.newScheduledThreadPool(4), 30_000);
        endpoint.start();
        connection.flush(Duration.ofSeconds(5));
        running.add(endpoint);
    }

    /** Placement as {@code PlacementService} wires it, with this network's pool in place of Agones. */
    private void startPlacement() throws Exception {
        final Placer[] placer = new Placer[1];
        final Connection connection = connect("placement", (conn, event) -> {
            if (event == ConnectionListener.Events.RECONNECTED && placer[0] != null) {
                placer[0].markReachable();
            }
        });
        final ServerPool servers = new ServerPool() {
            @Override
            public List<Slots> servers(String role) {
                return pool.getOrDefault(role, List.of());
            }

            @Override
            public Optional<String> allocate(String role) {
                return Optional.empty();
            }
        };
        placer[0] = new Placer(NatsLeaseStore.open(connection, prefix, 1), servers, site -> site, Duration.ofSeconds(60),
                System::currentTimeMillis);
        final PlacementResponder responder = new PlacementResponder(connection, placer[0], "placement");
        responder.start(prefix);
        connection.flush(Duration.ofSeconds(5));
        running.add(responder);
    }

    private void startLedger() throws Exception {
        final Connection connection = connect("ledger");
        final NatsLedgerEndpoint endpoint = new NatsLedgerEndpoint(connection, prefix,
                new LedgerRequests(new PostgresLedgerStore(ledgerDatabase)), Executors.newFixedThreadPool(4));
        endpoint.start();
        connection.flush(Duration.ofSeconds(5));
        running.add(endpoint);
    }

    private void startClans() throws Exception {
        final Connection connection = connect("clans");
        final ClanService service = new ClanService(connection, new ClanStore(clansDatabase), prefix);
        service.start();
        connection.flush(Duration.ofSeconds(5));
        running.add(service::stop);
    }

    /** The pool {@code ClansServiceApplication} builds. */
    private @NotNull HikariDataSource clansDataSource() {
        final HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(jdbcUrl());
        hikari.setUsername("postgres");
        hikari.setPassword("test");
        hikari.setMaximumPoolSize(2);
        return new HikariDataSource(hikari);
    }

    /** The clan tables as the clans plugin's migrations create them, which is where the service expects them. */
    private void createClanTables() throws Exception {
        final Pattern create = Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+)\\s*\\(.*?\\);", Pattern.DOTALL);
        final Set<String> tables = Set.of("clans", "clan_metadata", "clan_members", "clan_alliances", "clan_enemies",
                "clan_camp_permissions", "clan_properties", "clan_territory");
        try (var connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             var files = Files.list(Path.of("../clans/src/main/resources/clans-migrations/postgres"))) {
            for (Path file : files.sorted().toList()) {
                final Matcher matcher = create.matcher(Files.readString(file));
                while (matcher.find()) {
                    if (tables.contains(matcher.group(1))) {
                        statement.execute(matcher.group());
                    }
                }
            }
        }
    }

    private @NotNull String jdbcUrl() {
        return jdbcUrl("postgres");
    }

    private @NotNull String jdbcUrl(@NotNull String database) {
        return "jdbc:postgresql://localhost:" + postgresPort + "/" + database;
    }

    private static @NotNull GenericContainer<?> pinned(@NotNull GenericContainer<?> container, int containerPort,
                                                      int hostPort) {
        return container.withExposedPorts(containerPort)
                .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindPort(hostPort), new ExposedPort(containerPort))));
    }

    private static @NotNull DockerClient docker() {
        return DockerClientFactory.instance().client();
    }

    private static boolean isRunning(@NotNull GenericContainer<?> container) {
        return Boolean.TRUE.equals(docker().inspectContainerCmd(container.getContainerId()).exec().getState().getRunning());
    }

    private static boolean accepts(int port) {
        try (Socket ignored = new Socket("localhost", port)) {
            return true;
        } catch (IOException closed) {
            return false;
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
