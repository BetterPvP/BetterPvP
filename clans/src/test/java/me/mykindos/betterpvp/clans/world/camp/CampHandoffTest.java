package me.mykindos.betterpvp.clans.world.camp;

import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.core.world.site.SiteInstance;
import me.mykindos.betterpvp.core.world.site.SiteInstanceHandoffEvent;
import me.mykindos.betterpvp.core.world.site.SiteInstances;
import me.mykindos.betterpvp.core.world.site.SiteKey;
import me.mykindos.betterpvp.core.world.site.SiteOwners;
import me.mykindos.betterpvp.core.world.site.storage.SiteRecord;
import me.mykindos.betterpvp.core.world.site.storage.SiteStorage;
import me.mykindos.betterpvp.core.world.site.storage.StaleSiteRecordException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("Handing a camp to another server")
class CampHandoffTest {

    private static final long CLAN = 7;

    @Test
    @DisplayName("the camp is written down before the hand-off goes on, and the next server reads it whole")
    void theNextServerReadsWhatWasSaved() {
        final Delayed storage = new Delayed();
        final CampStore here = new CampStore(storage);
        final Camps camps = new Camps(mock(ClanManager.class), here, mock(SiteInstances.class), new SiteOwners());
        here.load(CLAN).join().setSkin("shore");
        here.changed(CLAN);

        final SiteInstanceHandoffEvent event = new SiteInstanceHandoffEvent(
                new SiteInstance(UUID.randomUUID(), Camps.keyFor(CLAN), "camps/7", SiteInstance.State.RELEASING), false);
        camps.onHandoff(event);
        final CompletableFuture<Void> saved = event.whenSaved();

        assertFalse(saved.isDone(), "the hand-off waits for the write");
        storage.finish();
        assertTrue(saved.isDone());

        assertEquals("shore", new CampStore(storage).load(CLAN).join().getSkin());
        assertTrue(here.cached(CLAN).isEmpty(), "this server lets go of the camp");
        here.flush().join();
        assertEquals(1, storage.writes, "nothing here writes it again");
    }

    /** Holds every write until {@link #finish}, and refuses one at a version other than the stored one. */
    private static final class Delayed implements SiteStorage {

        private final Map<SiteKey, SiteRecord> records = new HashMap<>();
        private final CompletableFuture<Void> released = new CompletableFuture<>();
        private int writes;

        void finish() {
            released.complete(null);
        }

        @Override
        public @NotNull CompletableFuture<Optional<SiteRecord>> read(@NotNull SiteKey key) {
            return CompletableFuture.completedFuture(Optional.ofNullable(records.get(key)));
        }

        @Override
        public @NotNull CompletableFuture<Long> write(@NotNull SiteKey key, byte @NotNull [] data, long version) {
            return released.thenApply(unused -> {
                final SiteRecord stored = records.get(key);
                if ((stored == null ? 0 : stored.getVersion()) != version) {
                    throw new StaleSiteRecordException(key, version);
                }
                writes++;
                records.put(key, new SiteRecord(data, version + 1));
                return version + 1;
            });
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
