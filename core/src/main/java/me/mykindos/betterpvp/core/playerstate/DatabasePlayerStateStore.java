package me.mykindos.betterpvp.core.playerstate;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.database.Database;
import org.jetbrains.annotations.NotNull;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record2;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

/** Keeps one row per player in the {@code player_states} table, guarded by its version. */
@Singleton
public class DatabasePlayerStateStore implements PlayerStateStore {

    private static final Table<?> TABLE = table(name("player_states"));
    private static final Field<UUID> CLIENT = field(name("client"), SQLDataType.UUID);
    private static final Field<Long> VERSION = field(name("version"), SQLDataType.BIGINT);
    private static final Field<Long> SAVED_AT = field(name("saved_at"), SQLDataType.BIGINT);
    private static final Field<JSONB> STATE = field(name("state"), SQLDataType.JSONB);

    private final Database database;
    private final PlayerStateCodec codec;

    @Inject
    public DatabasePlayerStateStore(@NotNull Database database, @NotNull PlayerStateCodec codec) {
        this.database = database;
        this.codec = codec;
    }

    @Override
    public @NotNull CompletableFuture<Optional<PlayerState>> load(@NotNull UUID playerId) {
        return database.getAsyncDslContext().executeAsync(ctx -> {
            final Record2<Long, JSONB> row = ctx.select(VERSION, STATE)
                    .from(TABLE)
                    .where(CLIENT.eq(playerId))
                    .fetchOne();
            return Optional.ofNullable(row).map(found -> codec.decode(playerId, found.value1(), found.value2().data()));
        });
    }

    @Override
    public @NotNull CompletableFuture<Boolean> save(@NotNull PlayerState state) {
        final JSONB document = JSONB.valueOf(codec.encode(state));
        return database.getAsyncDslContext().executeAsync(ctx -> ctx.insertInto(TABLE)
                .columns(CLIENT, VERSION, SAVED_AT, STATE)
                .values(state.getPlayerId(), state.getVersion(), System.currentTimeMillis(), document)
                .onConflict(CLIENT)
                .doUpdate()
                .set(VERSION, excluded(VERSION))
                .set(SAVED_AT, excluded(SAVED_AT))
                .set(STATE, excluded(STATE))
                .where(field(name("player_states", "version"), SQLDataType.BIGINT).lt(excluded(VERSION)))
                .execute() > 0);
    }

    @Override
    public @NotNull CompletableFuture<Void> fence(@NotNull UUID playerId, long version) {
        return database.getAsyncDslContext().executeAsyncVoid(ctx -> ctx.update(TABLE)
                .set(VERSION, version)
                .where(CLIENT.eq(playerId))
                .and(VERSION.lt(version))
                .execute());
    }
}
