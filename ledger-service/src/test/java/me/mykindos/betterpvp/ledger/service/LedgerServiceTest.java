package me.mykindos.betterpvp.ledger.service;

import com.zaxxer.hikari.HikariDataSource;
import io.nats.client.Connection;
import io.nats.client.Nats;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.ledger.LedgerEntry;
import me.mykindos.betterpvp.ledger.NatsLedgerTransport;
import me.mykindos.betterpvp.ledger.LedgerPosting;
import me.mykindos.betterpvp.ledger.LedgerProtocol;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import me.mykindos.betterpvp.network.protocol.Subjects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class LedgerServiceTest {

    private static final long OPENING = 1_000;

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    private static final GenericContainer<?> NATS = new GenericContainer<>("nats:2.10-alpine").withExposedPorts(4222);

    private static HikariDataSource dataSource;
    private static Connection serviceConnection;
    private static NatsLedgerEndpoint endpoint;
    private static GameServer serverA;
    private static GameServer serverB;

    @BeforeAll
    static void start() throws Exception {
        dataSource = LedgerServiceApplication.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword(), 16);
        LedgerServiceApplication.migrate(dataSource);
        serviceConnection = Nats.connect(natsUrl());
        endpoint = new NatsLedgerEndpoint(serviceConnection, Subjects.DEFAULT_PREFIX, new LedgerRequests(new PostgresLedgerStore(dataSource)),
                Executors.newFixedThreadPool(16));
        endpoint.start();
        serviceConnection.flush(Duration.ofSeconds(5));
        serverA = new GameServer("server-a");
        serverB = new GameServer("server-b");
    }

    @AfterAll
    static void stop() throws Exception {
        serverA.close();
        serverB.close();
        endpoint.close();
        serviceConnection.close();
        dataSource.close();
    }

    @Test
    void coinsMovedBetweenManyPlayersFromTwoServersEndBalanced() throws Exception {
        final List<String> players = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            final String player = LedgerAccounts.player(UUID.randomUUID());
            final GameServer opener = i % 2 == 0 ? serverA : serverB;
            assertEquals(OPENING, opener.open(player, OPENING).balance(player).orElseThrow());
            players.add(player);
        }

        final AtomicInteger posted = new AtomicInteger();
        final ExecutorService threads = Executors.newFixedThreadPool(16);
        final List<Future<?>> work = new ArrayList<>();
        for (int t = 0; t < 16; t++) {
            final GameServer server = t % 2 == 0 ? serverA : serverB;
            work.add(threads.submit(() -> {
                final ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < 60; i++) {
                    final String from = players.get(random.nextInt(players.size()));
                    String to = players.get(random.nextInt(players.size()));
                    while (to.equals(from)) {
                        to = players.get(random.nextInt(players.size()));
                    }
                    final LedgerReceipt receipt = server.post(LedgerPosting.of(
                            new LedgerEntry(from, to, random.nextLong(1, 400), "test.transfer")));
                    assertTrue(receipt.isPosted() || receipt.getStatus() == LedgerStatus.INSUFFICIENT_FUNDS,
                            "unexpected " + receipt.getStatus());
                    if (receipt.isPosted()) {
                        posted.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        for (Future<?> future : work) {
            future.get();
        }
        threads.shutdown();

        long total = 0;
        for (String player : players) {
            final long balance = balance(player);
            assertTrue(balance >= 0, player + " went negative");
            assertEquals(entriesInto(player) - entriesOutOf(player), balance, player + " disagrees with its entries");
            total += balance;
        }
        assertEquals(OPENING * players.size(), total);
        assertEquals(posted.get(), count("SELECT count(*) FROM ledger_entry WHERE reason = 'test.transfer'"));
        assertEquals(0, count("SELECT coalesce(sum(balance), 0) FROM ledger_account"));
    }

    @Test
    void buyingTheSameListingFromTwoServersSucceedsOnce() throws Exception {
        final String reference = "auction:" + UUID.randomUUID();
        final long price = 600;
        final List<String> buyers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            final String buyer = LedgerAccounts.player(UUID.randomUUID());
            serverA.open(buyer, OPENING);
            buyers.add(buyer);
        }

        final ExecutorService threads = Executors.newFixedThreadPool(buyers.size());
        final List<Callable<LedgerReceipt>> purchases = new ArrayList<>();
        for (int i = 0; i < buyers.size(); i++) {
            final GameServer server = i % 2 == 0 ? serverA : serverB;
            final LedgerPosting purchase = new LedgerPosting(List.of(
                    new LedgerEntry(buyers.get(i), LedgerAccounts.AUCTION_HOUSE, price, "auction.buy")), reference);
            purchases.add(() -> server.post(purchase));
        }
        final List<LedgerReceipt> receipts = new ArrayList<>();
        for (Future<LedgerReceipt> future : threads.invokeAll(purchases)) {
            receipts.add(future.get());
        }
        threads.shutdown();

        assertEquals(1, receipts.stream().filter(LedgerReceipt::isPosted).count());
        assertEquals(buyers.size() - 1, receipts.stream().filter(r -> r.getStatus() == LedgerStatus.DUPLICATE).count());
        final int winner = receipts.indexOf(receipts.stream().filter(LedgerReceipt::isPosted).findFirst().orElseThrow());
        for (int i = 0; i < buyers.size(); i++) {
            assertEquals(i == winner ? OPENING - price : OPENING, balance(buyers.get(i)));
        }

        final LedgerPosting retry = new LedgerPosting(List.of(
                new LedgerEntry(buyers.get(winner), LedgerAccounts.AUCTION_HOUSE, price, "auction.buy")), reference);
        assertTrue(serverB.post(retry).isPosted(), "a retry of the winning purchase is answered as posted");
        assertEquals(OPENING - price, balance(buyers.get(winner)), "a retry moves nothing");
    }

    @Test
    void depositingIntoOneClanBankFromTwoServersAtOnceEndsWithTheRightBalance() throws Exception {
        final long clanOpening = 250;
        final long deposit = 100;
        final int depositsEach = 3;
        final String clan = LedgerAccounts.clan(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
        assertEquals(clanOpening, serverA.open(clan, clanOpening).balance(clan).orElseThrow());
        final List<String> depositors = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final String player = LedgerAccounts.player(UUID.randomUUID());
            serverA.open(player, OPENING);
            depositors.add(player);
        }

        final ExecutorService threads = Executors.newFixedThreadPool(depositors.size());
        final List<Callable<List<LedgerReceipt>>> deposits = new ArrayList<>();
        for (int i = 0; i < depositors.size(); i++) {
            final GameServer server = i % 2 == 0 ? serverA : serverB;
            final String player = depositors.get(i);
            deposits.add(() -> {
                final List<LedgerReceipt> receipts = new ArrayList<>();
                for (int d = 0; d < depositsEach; d++) {
                    receipts.add(server.post(LedgerPosting.of(new LedgerEntry(player, clan, deposit, "clan.bank.deposit"))));
                }
                return receipts;
            });
        }
        for (Future<List<LedgerReceipt>> future : threads.invokeAll(deposits)) {
            for (LedgerReceipt receipt : future.get()) {
                assertTrue(receipt.isPosted(), "unexpected " + receipt.getStatus());
                assertTrue(receipt.balance(clan).isPresent(), "a posting answers the clan's balance");
            }
        }
        threads.shutdown();

        assertEquals(clanOpening + depositors.size() * depositsEach * deposit, balance(clan));
        for (String player : depositors) {
            assertEquals(OPENING - depositsEach * deposit, balance(player));
        }
        assertEquals(LedgerStatus.INSUFFICIENT_FUNDS, serverB.post(LedgerPosting.of(
                new LedgerEntry(clan, depositors.getFirst(), balance(clan) + 1, "clan.bank.withdraw"))).getStatus());
    }

    @Test
    void aPlayerCannotGoBelowZero() throws Exception {
        final String player = LedgerAccounts.player(UUID.randomUUID());
        serverA.open(player, 100);

        final LedgerReceipt receipt = serverA.post(LedgerPosting.of(
                new LedgerEntry(player, LedgerAccounts.SHOP, 101, "shop.buy")));

        assertEquals(LedgerStatus.INSUFFICIENT_FUNDS, receipt.getStatus());
        assertEquals(100, balance(player));
    }

    @Test
    void openingTwiceKeepsTheFirstBalance() throws Exception {
        final String player = LedgerAccounts.player(UUID.randomUUID());
        serverA.open(player, 300);

        assertEquals(300, serverB.open(player, 9_000).balance(player).orElseThrow());
    }

    private static long balance(String account) {
        return new PostgresLedgerStore(dataSource).balance(account);
    }

    private static long entriesInto(String account) throws Exception {
        return sum("SELECT coalesce(sum(amount), 0) FROM ledger_entry WHERE to_account = ?", account);
    }

    private static long entriesOutOf(String account) throws Exception {
        return sum("SELECT coalesce(sum(amount), 0) FROM ledger_entry WHERE from_account = ?", account);
    }

    private static long sum(String sql, String account) throws Exception {
        try (java.sql.Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(sql)) {
            select.setString(1, account);
            try (ResultSet row = select.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        }
    }

    private static long count(String sql) throws Exception {
        try (java.sql.Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement(sql);
             ResultSet row = select.executeQuery()) {
            row.next();
            return row.getLong(1);
        }
    }

    private static String natsUrl() {
        return "nats://" + NATS.getHost() + ":" + NATS.getMappedPort(4222);
    }

    /** A game server's side of the wire: its own NATS connection and the transport the plugin uses. */
    private static final class GameServer implements AutoCloseable {

        private final Connection connection;
        private final NatsLedgerTransport transport;

        GameServer(String name) throws Exception {
            this.connection = Nats.connect(natsUrl());
            this.transport = new NatsLedgerTransport(connection, Subjects.DEFAULT_PREFIX, name, Duration.ofSeconds(10));
        }

        LedgerReceipt post(LedgerPosting posting) {
            return ask(LedgerProtocol.POST, LedgerProtocol.encode(posting));
        }

        LedgerReceipt open(String account, long opening) {
            return ask(LedgerProtocol.OPEN, LedgerProtocol.encodeOpen(account, opening));
        }

        private LedgerReceipt ask(String topic, Map<String, String> payload) {
            final LedgerReceipt receipt = transport.ask(topic, payload).join();
            assertNotEquals(LedgerStatus.UNAVAILABLE, receipt.getStatus(), "the ledger did not answer " + topic);
            return receipt;
        }

        @Override
        public void close() throws InterruptedException {
            connection.close();
        }
    }
}
