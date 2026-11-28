package me.mykindos.betterpvp.clans.network;

import me.mykindos.betterpvp.clans.world.camp.CampStore;
import me.mykindos.betterpvp.core.network.Network;
import me.mykindos.betterpvp.core.world.site.storage.DatabaseSiteStorage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("A camp record written by two servers")
class StaleCampTest {

    private static final long CLAN = 8000;

    private static Network network;
    private static DatabaseSiteStorage storage;

    @BeforeAll
    static void start() throws Exception {
        MockBukkit.mock();
        network = new Network();
        // Only the table camp records live in, as its migration creates it
        try (InputStream migration = Objects.requireNonNull(StaleCampTest.class.getResourceAsStream(
                "/core-migrations/postgres/site/V20261126_1__Create_site_records.sql"));
             var connection = network.getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(new String(migration.readAllBytes(), StandardCharsets.UTF_8));
        }
        storage = new DatabaseSiteStorage(ClanServer.database(network.getDataSource()));
    }

    @AfterAll
    static void stop() throws Exception {
        if (network != null) {
            network.close();
        }
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("is read again by the server that was refused, whose next write then lands")
    void theRefusedServerCatchesUp() {
        final CampStore here = new CampStore(storage);
        final CampStore elsewhere = new CampStore(storage);
        here.load(CLAN).join().setSkin("here");
        elsewhere.load(CLAN).join().setSkin("elsewhere");
        elsewhere.save(CLAN).join();

        here.save(CLAN).join();
        assertEquals("elsewhere", here.cached(CLAN).orElseThrow().getSkin(), "the refused server kept its stale copy");

        here.cached(CLAN).orElseThrow().setSkin("here again");
        here.save(CLAN).join();
        assertEquals("here again", new CampStore(storage).load(CLAN).join().getSkin());
    }
}
