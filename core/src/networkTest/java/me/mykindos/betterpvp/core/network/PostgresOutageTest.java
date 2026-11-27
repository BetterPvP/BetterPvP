package me.mykindos.betterpvp.core.network;

import me.mykindos.betterpvp.clans.protocol.ClanChange;
import me.mykindos.betterpvp.clans.protocol.ClanMutation;
import me.mykindos.betterpvp.clans.protocol.ClanOp;
import me.mykindos.betterpvp.clans.protocol.ClanReply;
import me.mykindos.betterpvp.clans.protocol.ClanTopics;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.framework.economy.ledger.Movement;
import me.mykindos.betterpvp.core.playerstate.PlayerStateFixture;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Postgres going down")
class PostgresOutageTest {

    private static final Duration OUTAGE = Duration.ofSeconds(4);

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

    @AfterEach
    void backUp() {
        network.startPostgres();
    }

    @Test
    @DisplayName("keeps what a player picked up when they leave mid-outage")
    void playerLeavingDuringOutageKeepsEverything() throws Exception {
        final FakeServer source = network.server("camp-a");
        final FakeServer target = network.server("hub-1");
        final PlayerStateFixture items = network.getFixture();
        final UUID id = UUID.randomUUID();
        final PlayerMock player = source.arrive(id);
        final ItemStack kept = items.blade(1);
        player.getInventory().setItem(0, kept);
        assertTrue(source.getHandoff().save(player).join());

        final ItemStack pickedUp = items.blade(2);
        player.getInventory().setItem(1, pickedUp);
        network.stopPostgres();
        final CompletableFuture<Void> leaving = source.getHandoff().leave(player);
        Thread.sleep(OUTAGE.toMillis());
        network.startPostgres();
        leaving.join();
        player.disconnect();

        final PlayerMock arrived = target.arrive(id);
        items.assertSameItem(kept, arrived.getInventory().getItem(0));
        items.assertSameItem(pickedUp, arrived.getInventory().getItem(1));
    }

    @Test
    @DisplayName("moves each coin posting once, however often a server sends it")
    void postingsLandOnce() throws Exception {
        final FakeServer server = network.server("shop-1");
        final String buyer = LedgerAccounts.player(UUID.randomUUID());
        final AtomicLong coins = new AtomicLong(100);
        assertTrue(server.getLedger().open(LedgerParty.held(buyer, PlayerJourneyTest.balance(coins))).join().isPosted());

        network.stopPostgres();
        final List<CompletableFuture<LedgerReceipt>> during = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            during.add(buy(server, buyer, coins, "order-" + i));
        }
        Thread.sleep(OUTAGE.toMillis());
        network.startPostgres();
        for (CompletableFuture<LedgerReceipt> posting : during) {
            final LedgerStatus status = posting.join().getStatus();
            assertTrue(status == LedgerStatus.POSTED || status == LedgerStatus.UNAVAILABLE, status.name());
        }

        // What a server does with an order the ledger never confirmed: send it again under its reference
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 5; i++) {
                assertEquals(LedgerStatus.POSTED, buy(server, buyer, coins, "order-" + i).join().getStatus());
            }
        }
        assertEquals(50, coins.get());
        assertEquals(LedgerStatus.INSUFFICIENT_FUNDS, server.getLedger().post(List.of(new Movement(
                LedgerParty.held(buyer, PlayerJourneyTest.balance(coins)), LedgerParty.system(LedgerAccounts.SHOP),
                51, "too much")), null).join().getStatus());
    }

    @Test
    @DisplayName("tells servers about every clan write it applied, and only those")
    void clanWritesAreAppliedOnceOrNotAtAll() throws Exception {
        final FakeServer server = network.server("clans-1");
        final long clan = 3000;
        final Set<Long> announced = ConcurrentHashMap.newKeySet();
        server.getBus().subscribe(ClanTopics.CHANGED, message -> {
            final ClanChange change = ClanChange.fromPayload(message.getPayload());
            if (change.getOp() == ClanOp.PROPERTIES && change.getClans().contains(clan)) {
                announced.add(change.getSequence());
            }
        });
        server.getConnection().flush(Duration.ofSeconds(2));
        assertTrue(server.mutate(ClanMutation.builder().op(ClanOp.CREATE).clan(clan).name("Outage").realm(1).build())
                .isApplied());

        network.stopPostgres();
        final CompletableFuture<ClanReply> during = CompletableFuture.supplyAsync(() -> server.mutate(
                ClanMutation.builder().op(ClanOp.PROPERTIES).clan(clan).properties(Map.of("points", "7")).build()));
        Thread.sleep(OUTAGE.toMillis());
        network.startPostgres();
        final ClanReply reply = during.join();
        // A write that was in flight may still land once the database is back, and is then announced
        Thread.sleep(OUTAGE.toMillis());

        final String stored = property(clan, "points");
        assertEquals(reply.isApplied() || !announced.isEmpty(), "7".equals(stored),
                "a clan write landed that no server was told about, or was announced and not stored");
        assertTrue(server.mutate(ClanMutation.builder().op(ClanOp.PROPERTIES).clan(clan)
                .properties(Map.of("points", "8")).build()).isApplied());
        assertEquals("8", property(clan, "points"));
    }

    private static CompletableFuture<LedgerReceipt> buy(FakeServer server, String buyer, AtomicLong coins, String order) {
        return server.getLedger().post(List.of(new Movement(LedgerParty.held(buyer, PlayerJourneyTest.balance(coins)),
                LedgerParty.system(LedgerAccounts.SHOP), 10, "order")), order);
    }

    private static String property(long clan, String property) throws Exception {
        try (var connection = network.getDataSource().getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT value FROM clan_properties WHERE clan = " + clan
                     + " AND property = '" + property + "'")) {
            return result.next() ? result.getString(1) : null;
        }
    }
}
