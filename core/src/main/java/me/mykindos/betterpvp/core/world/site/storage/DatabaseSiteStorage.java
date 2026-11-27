package me.mykindos.betterpvp.core.world.site.storage;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.database.Database;
import me.mykindos.betterpvp.core.world.site.SiteKey;
import org.jetbrains.annotations.NotNull;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Table;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

/**
 * Site records in the {@code site_records} table, one row per site and owner, so any server reaches any record.
 * <p>
 * A write only lands on the row at the version it names, checked in the same statement that changes it, so two
 * servers writing the same record cannot both succeed.
 */
@Singleton
public class DatabaseSiteStorage implements SiteStorage {

    private static final Table<?> RECORDS = table(name("site_records"));
    private static final Field<byte[]> DATA = field(name("data"), byte[].class);
    private static final Field<Long> VERSION = field(name("version"), Long.class);

    private final Database database;

    @Inject
    public DatabaseSiteStorage(@NotNull Database database) {
        this.database = database;
    }

    @Override
    public @NotNull CompletableFuture<Optional<SiteRecord>> read(@NotNull SiteKey key) {
        return database.getAsyncDslContext().executeAsync(ctx -> ctx.select(DATA, VERSION)
                .from(RECORDS)
                .where(is(key))
                .fetchOptional()
                .map(DatabaseSiteStorage::toRecord));
    }

    @Override
    public @NotNull CompletableFuture<Long> write(@NotNull SiteKey key, byte @NotNull [] data, long version) {
        final long now = System.currentTimeMillis();
        return database.getAsyncDslContext().executeAsync(ctx -> version == 0
                        ? ctx.insertInto(RECORDS)
                        .columns(field(name("site"), String.class), field(name("owner"), Long.class), DATA, VERSION, field(name("updated_at"), Long.class))
                        .values(key.getSiteId(), key.getOwnerId(), data, 1L, now)
                        .onConflictDoNothing()
                        .execute()
                        : ctx.update(RECORDS)
                        .set(DATA, data)
                        .set(VERSION, version + 1)
                        .set(field(name("updated_at"), Long.class), now)
                        .where(is(key))
                        .and(VERSION.eq(version))
                        .execute())
                .thenApply(changed -> {
                    if (changed == 0) {
                        throw new StaleSiteRecordException(key, version);
                    }
                    return version + 1;
                });
    }

    @Override
    public @NotNull CompletableFuture<Void> delete(@NotNull SiteKey key) {
        return database.getAsyncDslContext().executeAsyncVoid(ctx -> ctx.deleteFrom(RECORDS)
                .where(is(key))
                .execute());
    }

    @Override
    public @NotNull CompletableFuture<Boolean> exists(@NotNull SiteKey key) {
        return database.getAsyncDslContext().executeAsync(ctx -> ctx.fetchExists(RECORDS, is(key)));
    }

    private static @NotNull Condition is(@NotNull SiteKey key) {
        return field(name("site"), String.class).eq(key.getSiteId())
                .and(field(name("owner"), Long.class).eq(key.getOwnerId()));
    }

    private static @NotNull SiteRecord toRecord(@NotNull Record2<byte[], Long> row) {
        return new SiteRecord(row.value1(), row.value2());
    }
}
