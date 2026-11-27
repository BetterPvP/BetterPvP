package me.mykindos.betterpvp.clans.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Nats;
import me.mykindos.betterpvp.clans.protocol.ClanChange;
import me.mykindos.betterpvp.clans.protocol.ClanMutation;
import me.mykindos.betterpvp.clans.protocol.ClanOp;
import me.mykindos.betterpvp.clans.protocol.ClanReply;
import me.mykindos.betterpvp.clans.protocol.ClanTopics;
import me.mykindos.betterpvp.network.protocol.EnvelopeCodec;
import me.mykindos.betterpvp.network.protocol.NetworkEnvelope;
import me.mykindos.betterpvp.network.protocol.Subjects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class ClanServiceTest {

    private static final Set<String> CLAN_TABLES = Set.of("clans", "clan_metadata", "clan_members", "clan_alliances",
            "clan_enemies", "clan_camp_permissions");
    private static final Path MIGRATIONS = Path.of("../clans/src/main/resources/clans-migrations/postgres");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    private static final GenericContainer<?> NATS = new GenericContainer<>("nats:2.10-alpine")
            .withExposedPorts(4222)
            .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1));

    private static final String PREFIX = "test";
    private static final EnvelopeCodec CODEC = new EnvelopeCodec();
    private static HikariDataSource dataSource;

    private Connection serviceConnection;
    private ClanService service;
    private final List<Connection> servers = new ArrayList<>();

    @BeforeAll
    static void createSchema() throws Exception {
        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        try (java.sql.Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String table : clanTableDefinitions()) {
                statement.execute(table);
            }
        }
    }

    @AfterAll
    static void closeDataSource() {
        dataSource.close();
    }

    @BeforeEach
    void startService() throws Exception {
        try (java.sql.Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE clans CASCADE");
        }
        serviceConnection = connect();
        service = new ClanService(serviceConnection, new ClanStore(dataSource), PREFIX);
        service.start();
        serviceConnection.flush(Duration.ofSeconds(2));
    }

    @AfterEach
    void stopService() throws InterruptedException {
        service.stop();
        serviceConnection.close();
        for (Connection server : servers) {
            server.close();
        }
        servers.clear();
    }

    @Test
    void twoServersEditingTheSameClanAtOnceBothApplyInOrder() throws Exception {
        final Connection first = server();
        final Connection second = server();
        final Connection watcher = server();
        final long clan = 1L;
        final UUID shared = UUID.randomUUID();
        assertTrue(send(first, create(clan, "Alpha", UUID.randomUUID())).isApplied());
        assertTrue(send(first, member(ClanOp.MEMBER_ADD, clan, shared, "RECRUIT")).isApplied());

        final List<Long> announced = Collections.synchronizedList(new ArrayList<>());
        final Dispatcher dispatcher = watcher.createDispatcher(message -> announced.add(change(message).getSequence()));
        dispatcher.subscribe(Subjects.bus(PREFIX, ClanTopics.CHANGED));
        watcher.flush(Duration.ofSeconds(2));

        final int perServer = 15;
        final CountDownLatch start = new CountDownLatch(1);
        final CompletableFuture<List<ClanReply>> fromFirst = editConcurrently(first, clan, shared, perServer, start);
        final CompletableFuture<List<ClanReply>> fromSecond = editConcurrently(second, clan, shared, perServer, start);
        start.countDown();

        final List<ClanReply> replies = new ArrayList<>(fromFirst.get(10, TimeUnit.SECONDS));
        replies.addAll(fromSecond.get(10, TimeUnit.SECONDS));
        assertTrue(replies.stream().allMatch(ClanReply::isApplied), () -> "Refused: " + replies);

        final Set<Long> sequences = new HashSet<>();
        replies.forEach(reply -> sequences.add(reply.getSequence()));
        assertEquals(replies.size(), sequences.size(), "Every mutation gets its own place in the order");

        awaitSize(announced, replies.size());
        for (int i = 1; i < announced.size(); i++) {
            assertEquals(announced.get(i - 1) + 1, announced.get(i), "Changes are announced in the order they applied");
        }

        // Leader, the shared recruit, and every member each server added.
        assertEquals(2 + 2 * perServer, count("SELECT COUNT(*) FROM clan_members WHERE clan = " + clan));
        // Both servers promoted the shared recruit once, and both promotions landed.
        assertEquals("ADMIN", text("SELECT rank FROM clan_members WHERE member = '" + shared + "'"));
    }

    @Test
    void aChangeOnOneServerShowsOnAnotherWithinASecond() throws Exception {
        final Connection first = server();
        final Connection second = server();
        assertTrue(send(first, create(1L, "Alpha", UUID.randomUUID())).isApplied());
        assertTrue(send(first, create(2L, "Bravo", UUID.randomUUID())).isApplied());

        final CountDownLatch seen = new CountDownLatch(1);
        final AtomicLong seenAfter = new AtomicLong();
        final long[] sentAt = new long[1];
        final Dispatcher dispatcher = second.createDispatcher(message -> {
            final ClanChange change = change(message);
            if (change.getOp() == ClanOp.ALLY && change.getClans().containsAll(List.of(1L, 2L))
                    && count("SELECT COUNT(*) FROM clan_alliances WHERE clan = 2 AND ally_clan = 1") == 1) {
                seenAfter.set(System.nanoTime() - sentAt[0]);
                seen.countDown();
            }
        });
        dispatcher.subscribe(Subjects.bus(PREFIX, ClanTopics.CHANGED));
        second.flush(Duration.ofSeconds(2));

        sentAt[0] = System.nanoTime();
        assertTrue(send(first, ClanMutation.builder().op(ClanOp.ALLY).clan(1L).other(2L).build()).isApplied());

        assertTrue(seen.await(1, TimeUnit.SECONDS), "The other server read the alliance within a second");
        assertTrue(Duration.ofNanos(seenAfter.get()).compareTo(Duration.ofSeconds(1)) < 0);
    }

    @Test
    void aPlayerJoinedToTwoClansFromTwoServersEndsUpInOne() throws Exception {
        final Connection first = server();
        final Connection second = server();
        final UUID player = UUID.randomUUID();
        assertTrue(send(first, create(1L, "Alpha", UUID.randomUUID())).isApplied());
        assertTrue(send(first, create(2L, "Bravo", UUID.randomUUID())).isApplied());

        final CompletableFuture<ClanReply> joinFirst = CompletableFuture.supplyAsync(
                () -> send(first, member(ClanOp.MEMBER_ADD, 1L, player, "RECRUIT")));
        final CompletableFuture<ClanReply> joinSecond = CompletableFuture.supplyAsync(
                () -> send(second, member(ClanOp.MEMBER_ADD, 2L, player, "RECRUIT")));

        assertTrue(joinFirst.get(5, TimeUnit.SECONDS).isApplied() ^ joinSecond.get(5, TimeUnit.SECONDS).isApplied());
        assertEquals(1, count("SELECT COUNT(*) FROM clan_members WHERE member = '" + player + "'"));
    }

    @Test
    void aTakenNameIsRefused() {
        final Connection server = server();
        assertTrue(send(server, create(1L, "Alpha", UUID.randomUUID())).isApplied());

        final ClanReply reply = send(server, create(2L, "alpha", UUID.randomUUID()));

        assertFalse(reply.isApplied());
        assertEquals(0, count("SELECT COUNT(*) FROM clans WHERE id = 2"));
    }

    @Test
    void disbandingRemovesTheClanAndItsRelations() {
        final Connection server = server();
        assertTrue(send(server, create(1L, "Alpha", UUID.randomUUID())).isApplied());
        assertTrue(send(server, create(2L, "Bravo", UUID.randomUUID())).isApplied());
        assertTrue(send(server, ClanMutation.builder().op(ClanOp.ENEMY).clan(1L).other(2L).build()).isApplied());
        assertTrue(send(server, ClanMutation.builder().op(ClanOp.CAMP_PERMISSION).clan(1L).scope("ALLY_CONTAINERS")
                .flag(true).build()).isApplied());

        assertTrue(send(server, ClanMutation.builder().op(ClanOp.DISBAND).clan(1L).build()).isApplied());

        assertEquals(0, count("SELECT COUNT(*) FROM clans WHERE id = 1"));
        assertEquals(0, count("SELECT COUNT(*) FROM clan_enemies"));
        assertEquals(0, count("SELECT COUNT(*) FROM clan_camp_permissions"));
    }

    private CompletableFuture<List<ClanReply>> editConcurrently(Connection server, long clan, UUID shared, int count,
                                                                CountDownLatch start) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                start.await();
            } catch (InterruptedException ex) {
                throw new IllegalStateException(ex);
            }
            final List<ClanReply> replies = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                replies.add(send(server, member(ClanOp.MEMBER_ADD, clan, UUID.randomUUID(), "RECRUIT")));
            }
            replies.add(send(server, member(ClanOp.MEMBER_PROMOTE, clan, shared, null)));
            return replies;
        });
    }

    private static ClanMutation create(long clan, String name, UUID leader) {
        return ClanMutation.builder().op(ClanOp.CREATE).clan(clan).name(name).realm(1).member(leader).build();
    }

    private static ClanMutation member(ClanOp op, long clan, UUID member, String rank) {
        return ClanMutation.builder().op(op).clan(clan).member(member).rank(rank).build();
    }

    private static ClanReply send(Connection server, ClanMutation mutation) {
        final NetworkEnvelope request = new NetworkEnvelope(ClanTopics.MUTATE, "test", UUID.randomUUID().toString(),
                false, mutation.toPayload());
        try {
            final Message reply = server.request(Subjects.bus(PREFIX, ClanTopics.MUTATE), CODEC.encode(request),
                    Duration.ofSeconds(5));
            if (reply == null) {
                throw new IllegalStateException("The service did not answer " + mutation.getOp());
            }
            return ClanReply.fromPayload(CODEC.decode(reply.getData()).getPayload());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static ClanChange change(Message message) {
        try {
            return ClanChange.fromPayload(CODEC.decode(message.getData()).getPayload());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Connection server() {
        try {
            final Connection connection = connect();
            servers.add(connection);
            return connection;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static Connection connect() throws IOException, InterruptedException {
        return Nats.connect("nats://" + NATS.getHost() + ":" + NATS.getMappedPort(4222));
    }

    private static void awaitSize(List<?> list, int size) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (list.size() < size && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(size, list.size());
    }

    private static long count(String sql) {
        return Long.parseLong(text(sql));
    }

    private static String text(String sql) {
        try (java.sql.Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** The clan tables as the plugin's migrations create them, so the test schema cannot drift from the real one. */
    private static List<String> clanTableDefinitions() throws IOException {
        final Pattern create = Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+)\\s*\\(.*?\\);", Pattern.DOTALL);
        final List<String> definitions = new ArrayList<>();
        try (var files = Files.list(MIGRATIONS)) {
            for (Path file : files.sorted().toList()) {
                final Matcher matcher = create.matcher(Files.readString(file));
                while (matcher.find()) {
                    if (CLAN_TABLES.contains(matcher.group(1))) {
                        definitions.add(matcher.group());
                    }
                }
            }
        }
        return definitions;
    }
}
