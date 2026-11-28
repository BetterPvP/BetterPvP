package me.mykindos.betterpvp.core.network;

import me.mykindos.betterpvp.clans.protocol.ClanChange;
import me.mykindos.betterpvp.clans.protocol.ClanMutation;
import me.mykindos.betterpvp.clans.protocol.ClanOp;
import me.mykindos.betterpvp.clans.protocol.ClanReply;
import me.mykindos.betterpvp.clans.protocol.ClanTopics;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.framework.economy.ledger.Movement;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Two servers writing the same clan at once")
class SingleWriterTest {

    private static final int WRITES = 25;

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
    @DisplayName("end with the clan as the service applied the writes, one after another")
    void clanWritesLandInOneOrder() throws Exception {
        final FakeServer first = network.server("clans-1");
        final FakeServer second = network.server("clans-2");
        final long clan = 1000;
        final Map<Long, ClanOp> announced = new ConcurrentHashMap<>();
        first.getBus().subscribe(ClanTopics.CHANGED, message -> {
            final ClanChange change = ClanChange.fromPayload(message.getPayload());
            if (change.getClans().contains(clan)) {
                announced.put(change.getSequence(), change.getOp());
            }
        });
        first.getConnection().flush(Duration.ofSeconds(2));
        assertTrue(first.mutate(ClanMutation.builder().op(ClanOp.CREATE).clan(clan).name("Writers").realm(1)
                .member(UUID.randomUUID()).build()).isApplied());

        final Map<Long, String> pointsBySequence = new ConcurrentHashMap<>();
        final AtomicInteger members = new AtomicInteger();
        final List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (int i = 0; i < WRITES; i++) {
            writes.add(write(first, clan, "a" + i, pointsBySequence, members));
            writes.add(write(second, clan, "b" + i, pointsBySequence, members));
        }
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();

        assertEquals(2 * WRITES, members.get(), "a member write was refused");
        assertEquals(2 * WRITES, pointsBySequence.size(), "a property write was refused or two shared a sequence");
        final long last = pointsBySequence.keySet().stream().mapToLong(Long::longValue).max().orElseThrow();
        assertEquals(pointsBySequence.get(last), property(clan, "points"),
                "the stored value is not the last one the service applied");
        assertEquals(2 * WRITES + 1, count("SELECT COUNT(*) FROM clan_members WHERE clan = " + clan));
        Network.await("every change to be announced", () -> announced.size() == 4 * WRITES + 1);
        final long firstSequence = announced.keySet().stream().mapToLong(Long::longValue).min().orElseThrow();
        assertEquals(LongStream.range(firstSequence, firstSequence + 4 * WRITES + 1).boxed().toList(),
                announced.keySet().stream().sorted().toList(), "announced sequences have gaps or repeats");
    }

    @Test
    @DisplayName("never spend the same clan coins twice, across both ledger replicas")
    void clanCoinsSpendOnce() throws Exception {
        final FakeServer first = network.server("coins-1");
        final FakeServer second = network.server("coins-2");
        final String clanAccount = LedgerAccounts.clan(2000);
        final AtomicLong firstView = new AtomicLong(100);
        final AtomicLong secondView = new AtomicLong(100);
        assertTrue(first.getLedger().open(LedgerParty.held(clanAccount, PlayerJourneyTest.balance(firstView))).join()
                .isPosted());

        final List<CompletableFuture<LedgerReceipt>> withdrawals = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            withdrawals.add(withdraw(first, clanAccount, firstView, "w-a" + i));
            withdrawals.add(withdraw(second, clanAccount, secondView, "w-b" + i));
        }
        final Map<LedgerStatus, Integer> outcomes = new HashMap<>();
        final List<Long> sequences = new ArrayList<>();
        for (CompletableFuture<LedgerReceipt> withdrawal : withdrawals) {
            final LedgerReceipt receipt = withdrawal.join();
            outcomes.merge(receipt.getStatus(), 1, Integer::sum);
            if (receipt.isPosted()) {
                sequences.add(receipt.getSequence());
            }
        }

        assertEquals(Map.of(LedgerStatus.POSTED, 10, LedgerStatus.INSUFFICIENT_FUNDS, 10), outcomes);
        assertEquals(sequences.size(), sequences.stream().distinct().count(), "two postings share a sequence");
        assertEquals(LedgerStatus.INSUFFICIENT_FUNDS, withdraw(first, clanAccount, firstView, "after").join().getStatus());
        assertEquals(0, Math.min(firstView.get(), secondView.get()), "no server saw the clan reach zero");
    }

    private static CompletableFuture<Void> write(FakeServer server, long clan, String points, Map<Long, String> applied,
                                                 AtomicInteger members) {
        return CompletableFuture.runAsync(() -> {
            final ClanReply member = server.mutate(ClanMutation.builder().op(ClanOp.MEMBER_ADD).clan(clan)
                    .member(UUID.randomUUID()).rank("MEMBER").build());
            final ClanReply property = server.mutate(ClanMutation.builder().op(ClanOp.PROPERTIES).clan(clan)
                    .properties(Map.of("points", points)).build());
            if (member.isApplied()) {
                members.incrementAndGet();
            }
            if (property.isApplied()) {
                applied.put(property.getSequence(), points);
            }
        });
    }

    private static CompletableFuture<LedgerReceipt> withdraw(FakeServer server, String clanAccount, AtomicLong view,
                                                             String reference) {
        final Movement movement = new Movement(LedgerParty.held(clanAccount, PlayerJourneyTest.balance(view)),
                LedgerParty.system(LedgerAccounts.CAMP), 10, "upkeep");
        return server.getLedger().post(List.of(movement), reference);
    }

    private static String property(long clan, String property) throws Exception {
        try (var connection = network.getDataSource().getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT value FROM clan_properties WHERE clan = " + clan
                     + " AND property = '" + property + "'")) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private static long count(String query) throws Exception {
        try (var connection = network.getDataSource().getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            result.next();
            return result.getLong(1);
        }
    }
}
