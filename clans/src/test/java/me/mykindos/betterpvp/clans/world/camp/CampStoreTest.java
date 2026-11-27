package me.mykindos.betterpvp.clans.world.camp;

import me.mykindos.betterpvp.core.world.site.SiteKey;
import me.mykindos.betterpvp.core.world.site.storage.SiteRecord;
import me.mykindos.betterpvp.core.world.site.storage.SiteStorage;
import me.mykindos.betterpvp.core.world.site.storage.StaleSiteRecordException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Writing camp records at the version they were read at")
class CampStoreTest {

    private static final long CLAN = 7;

    @Test
    @DisplayName("writes one after another each carry the version the last one left")
    void writesFollowEachOther() {
        final Versioned storage = new Versioned();
        final CampStore store = new CampStore(storage);
        store.load(CLAN).join().setSkin("shore");

        store.save(CLAN);
        store.save(CLAN);
        store.save(CLAN).join();

        assertEquals(3, storage.records.get(Camps.keyFor(CLAN)).getVersion());
    }

    @Test
    @DisplayName("a camp another server wrote since it was read is not overwritten")
    void aStaleCampIsNotOverwritten() {
        final Versioned storage = new Versioned();
        final CampStore here = new CampStore(storage);
        here.load(CLAN).join().setSkin("here");

        final CampStore elsewhere = new CampStore(storage);
        elsewhere.load(CLAN).join().setSkin("elsewhere");
        elsewhere.save(CLAN).join();

        here.save(CLAN).join();

        assertEquals("elsewhere", new CampStore(storage).load(CLAN).join().getSkin());
    }

    @Test
    @DisplayName("a camp refused as stale is read again, so the next write lands on top of the newer one")
    void aStaleCampIsReadAgain() {
        final Versioned storage = new Versioned();
        final CampStore here = new CampStore(storage);
        here.load(CLAN).join().setSkin("here");
        final CampStore elsewhere = new CampStore(storage);
        elsewhere.load(CLAN).join().setSkin("elsewhere");
        elsewhere.save(CLAN).join();

        here.save(CLAN).join();
        here.cached(CLAN).orElseThrow().setSkin(here.cached(CLAN).orElseThrow().getSkin() + " and here");
        here.save(CLAN).join();

        assertEquals("elsewhere and here", new CampStore(storage).load(CLAN).join().getSkin());
    }

    /** Refuses a write that names a version other than the stored one, as every store must. */
    private static final class Versioned implements SiteStorage {

        private final Map<SiteKey, SiteRecord> records = new HashMap<>();

        @Override
        public @NotNull CompletableFuture<Optional<SiteRecord>> read(@NotNull SiteKey key) {
            return CompletableFuture.completedFuture(Optional.ofNullable(records.get(key)));
        }

        @Override
        public @NotNull CompletableFuture<Long> write(@NotNull SiteKey key, byte @NotNull [] data, long version) {
            final SiteRecord stored = records.get(key);
            if ((stored == null ? 0 : stored.getVersion()) != version) {
                return CompletableFuture.failedFuture(new StaleSiteRecordException(key, version));
            }
            records.put(key, new SiteRecord(data, version + 1));
            return CompletableFuture.completedFuture(version + 1);
        }

        @Override
        public @NotNull CompletableFuture<Void> delete(@NotNull SiteKey key) {
            records.remove(key);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public @NotNull CompletableFuture<Boolean> exists(@NotNull SiteKey key) {
            return CompletableFuture.completedFuture(records.containsKey(key));
        }
    }
}
