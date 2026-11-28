package me.mykindos.betterpvp.clans.network;

import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.protocol.ClanMutation;
import me.mykindos.betterpvp.clans.protocol.ClanOp;
import me.mykindos.betterpvp.core.components.clans.data.ClanEnemy;
import me.mykindos.betterpvp.core.network.Network;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Kills between two enemies on two servers at once")
class DominanceKillTest {

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
    @DisplayName("each count toward the killer's dominance")
    void bothKillsCount() throws Exception {
        final ClanServer first = new ClanServer(network, "kills-1");
        final ClanServer second = new ClanServer(network, "kills-2");
        final long killer = 5000;
        final long killed = 5001;
        assertTrue(first.server.mutate(ClanMutation.builder().op(ClanOp.CREATE).clan(killer).name("Hunters").realm(1)
                .member(UUID.randomUUID()).build()).isApplied());
        assertTrue(first.server.mutate(ClanMutation.builder().op(ClanOp.CREATE).clan(killed).name("Hunted").realm(1)
                .member(UUID.randomUUID()).build()).isApplied());
        assertTrue(first.server.mutate(ClanMutation.builder().op(ClanOp.ENEMY).clan(killer).other(killed).build())
                .isApplied());
        assertTrue(first.server.mutate(ClanMutation.builder().op(ClanOp.DOMINANCE).clan(killer).other(killed)
                .value("40").build()).isApplied());

        // Each server saw 40 before its own kill, and applied the kill to that
        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> kill(first, killer, killed)),
                CompletableFuture.runAsync(() -> kill(second, killer, killed))).join();

        Network.await("both kills to be stored", () -> dominance(killer, killed) == 52);
        assertEquals(0, dominance(killed, killer));
    }

    private static void kill(ClanServer server, long killerId, long killedId) {
        final Clan killer = server.hold(killerId, 0);
        final Clan killed = server.hold(killedId, 0);
        final ClanEnemy killerEnemy = new ClanEnemy(killed, 40);
        final ClanEnemy killedEnemy = new ClanEnemy(killer, 0);
        killerEnemy.addDominance(6);
        killedEnemy.takeDominance(6);
        server.writer.killDominance(killer, killerEnemy, killed, killedEnemy, 6);
    }

    private static int dominance(long clan, long enemy) {
        try (var connection = network.getDataSource().getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT dominance FROM clan_enemies WHERE clan = " + clan
                     + " AND enemy_clan = " + enemy)) {
            return result.next() ? result.getInt(1) : -1;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
