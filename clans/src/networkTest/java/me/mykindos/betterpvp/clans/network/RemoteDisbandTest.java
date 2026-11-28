package me.mykindos.betterpvp.clans.network;

import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.events.ClanDisbandedElsewhereEvent;
import me.mykindos.betterpvp.clans.protocol.ClanMutation;
import me.mykindos.betterpvp.clans.protocol.ClanOp;
import me.mykindos.betterpvp.core.network.Network;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("A clan disbanded on another server")
class RemoteDisbandTest {

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
    @DisplayName("is let go of here too, so its camp, zone and records go with it")
    void isLetGoOfHere() throws Exception {
        final ClanServer hub = new ClanServer(network, "hub-1");
        final ClanServer camp = new ClanServer(network, "camp-1");
        final long id = 4000;
        assertTrue(hub.server.mutate(ClanMutation.builder().op(ClanOp.CREATE).clan(id).name("Leavers").realm(1)
                .member(UUID.randomUUID()).build()).isApplied());
        final Clan held = camp.hold(id, 0);
        final List<Clan> letGo = new CopyOnWriteArrayList<>();
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onDisbandedElsewhere(ClanDisbandedElsewhereEvent event) {
                letGo.add(event.getClan());
            }
        }, MockBukkit.createMockPlugin());
        camp.server.getConnection().flush(Duration.ofSeconds(2));

        // As the disband listener does on the server the clan was disbanded on
        hub.writer.disband(hub.hold(id, 0));
        hub.held.remove(id);

        Network.await("the camp server to let the clan go", () -> !camp.held.containsKey(id));
        assertEquals(List.of(held), letGo);
        assertFalse(camp.held.containsKey(id));
    }
}
