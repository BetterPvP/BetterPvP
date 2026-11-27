package me.mykindos.betterpvp.core.world.site.storage;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import me.mykindos.betterpvp.core.database.AsyncDSLContext;
import me.mykindos.betterpvp.core.database.Database;
import me.mykindos.betterpvp.core.database.connection.IDatabaseConnection;
import me.mykindos.betterpvp.core.world.site.SiteKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Keeping site records in Postgres")
class DatabaseSiteStorageTest {

    private static final SiteKey CAMP = SiteKey.of("camp", 7L);
    private static final SiteKey OTHER = SiteKey.of("camp", 8L);

    private static EmbeddedPostgres postgres;

    @TempDir
    private Path dataFolder;

    private DatabaseSiteStorage storage;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.start();
        final String migration = new String(DatabaseSiteStorageTest.class.getClassLoader()
                .getResourceAsStream("core-migrations/postgres/site/V20261126_1__Create_site_records.sql")
                .readAllBytes(), StandardCharsets.UTF_8);
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(migration);
        }
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        postgres.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        final DataSource dataSource = postgres.getPostgresDatabase();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE site_records");
        }
        storage = storageOn(dataSource);
    }

    /** A fresh store over the same database, which is all a restarted server has. */
    private static DatabaseSiteStorage storageOn(DataSource dataSource) {
        final IDatabaseConnection connection = mock(IDatabaseConnection.class);
        when(connection.getDataSource()).thenReturn(dataSource);
        final Database database = mock(Database.class);
        when(database.getAsyncDslContext()).thenReturn(new AsyncDSLContext(connection, Runnable::run));
        return new DatabaseSiteStorage(database);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(SiteRecord record) {
        return new String(record.getData(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a camp record survives a restart with plugins/Core/sites/ deleted")
    void aRecordSurvivesARestartWithNoLocalFiles() throws Exception {
        Files.createDirectories(dataFolder.resolve("sites"));
        storage.write(CAMP, bytes("the camp"), 0).join();
        Files.delete(dataFolder.resolve("sites"));

        final SiteRecord read = storageOn(postgres.getPostgresDatabase()).read(CAMP).join().orElseThrow();
        assertEquals("the camp", text(read));
        assertEquals(1, read.getVersion());
    }

    @Test
    @DisplayName("a write with an old version is rejected")
    void aStaleWriteIsRejected() {
        final long first = storage.write(CAMP, bytes("first"), 0).join();
        final long second = storage.write(CAMP, bytes("second"), first).join();

        final CompletionException stale = assertThrows(CompletionException.class,
                () -> storage.write(CAMP, bytes("from an old read"), first).join());
        assertInstanceOf(StaleSiteRecordException.class, stale.getCause());
        assertEquals("second", text(storage.read(CAMP).join().orElseThrow()));
        assertEquals(2, second);
    }

    @Test
    @DisplayName("creating a record that already exists is rejected")
    void creatingTwiceIsRejected() {
        storage.write(CAMP, bytes("first"), 0).join();

        final CompletionException stale = assertThrows(CompletionException.class,
                () -> storage.write(CAMP, bytes("again"), 0).join());
        assertInstanceOf(StaleSiteRecordException.class, stale.getCause());
    }

    @Test
    @DisplayName("two owners of one site are kept apart")
    void ownersAreKeptApart() {
        storage.write(CAMP, bytes("mine"), 0).join();
        storage.write(OTHER, bytes("theirs"), 0).join();

        assertEquals("mine", text(storage.read(CAMP).join().orElseThrow()));
        assertEquals("theirs", text(storage.read(OTHER).join().orElseThrow()));
    }

    @Test
    @DisplayName("a site nothing was written for reads as empty, and a deleted one is gone")
    void emptyAndDeleted() {
        assertTrue(storage.read(CAMP).join().isEmpty());
        assertFalse(storage.exists(CAMP).join());

        storage.write(CAMP, bytes("the camp"), 0).join();
        assertTrue(storage.exists(CAMP).join());
        storage.delete(CAMP).join();

        assertTrue(storage.read(CAMP).join().isEmpty());
        assertEquals(1, storage.write(CAMP, bytes("a new camp"), 0).join());
    }

    @Test
    @DisplayName("existing JSON records migrate on first start")
    void jsonRecordsImportOnce() throws Exception {
        final Path sites = dataFolder.resolve("sites");
        Files.createDirectories(sites.resolve("camp"));
        Files.writeString(sites.resolve("camp").resolve("7.json"), "{\"skin\":\"shore\"}");
        Files.writeString(sites.resolve("camp").resolve("8.json.writing"), "half");
        storage.write(OTHER, bytes("already here"), 0).join();
        Files.writeString(sites.resolve("camp").resolve("8.json"), "{\"skin\":\"stale\"}");

        assertEquals(1, new LocalSiteRecordImport(sites, storage).run());

        assertEquals("{\"skin\":\"shore\"}", text(storage.read(CAMP).join().orElseThrow()));
        assertEquals("already here", text(storage.read(OTHER).join().orElseThrow()));
        assertFalse(Files.exists(sites));
        assertTrue(Files.isRegularFile(dataFolder.resolve("sites.imported").resolve("camp").resolve("7.json")));
        assertEquals(0, new LocalSiteRecordImport(sites, storage).run());
    }
}
