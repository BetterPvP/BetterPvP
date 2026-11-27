package me.mykindos.betterpvp.clans.network;

import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.repository.ClanRepository;
import me.mykindos.betterpvp.clans.clans.writer.ClanChangeFeed;
import me.mykindos.betterpvp.clans.clans.writer.ServiceClanBank;
import me.mykindos.betterpvp.clans.clans.writer.ServiceClanWriter;
import me.mykindos.betterpvp.clans.world.camp.CampPermissions;
import me.mykindos.betterpvp.core.database.AsyncDSLContext;
import me.mykindos.betterpvp.core.database.Database;
import me.mykindos.betterpvp.core.database.connection.IDatabaseConnection;
import me.mykindos.betterpvp.core.database.mappers.PropertyMapper;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerBalance;
import me.mykindos.betterpvp.core.logging.repository.LogRepository;
import me.mykindos.betterpvp.core.network.FakeServer;
import me.mykindos.betterpvp.core.network.Network;
import org.jetbrains.annotations.NotNull;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A game server's clan side, built from the plugin's own writer, bank, change feed and repository over a
 * {@link FakeServer}. The plugin is never enabled, so work it would schedule on the main thread runs where it is
 * asked for.
 */
final class ClanServer {

    final FakeServer server;
    final Clans clans = mock(Clans.class);
    final ClanManager clanManager = mock(ClanManager.class, RETURNS_DEEP_STUBS);
    final Map<Long, Clan> held = new ConcurrentHashMap<>();
    final ClanRepository repository;
    final ClanChangeFeed feed;
    final ServiceClanWriter writer;
    final ServiceClanBank bank;

    ClanServer(@NotNull Network network, @NotNull String name) throws Exception {
        server = network.server(name);
        repository = new ClanRepository(database(network.getDataSource()), mock(PropertyMapper.class),
                mock(LogRepository.class));
        when(clanManager.getObjects()).thenReturn(held);
        when(clanManager.getClanById(any())).thenAnswer(ask -> Optional.ofNullable(held.get(ask.<Long>getArgument(0))));
        bank = new ServiceClanBank(clans, server.getLedger(), server.getBus(), clanManager);
        feed = new ClanChangeFeed(clans, clanManager, repository, () -> mock(CampPermissions.class), () -> bank);
        writer = new ServiceClanWriter(clans, server.getBus(), feed, repository);
    }

    /** A clan this server holds, with lists that can change and a balance that can be set. */
    @NotNull Clan hold(long id, int balance) {
        final Clan clan = mock(Clan.class, RETURNS_DEEP_STUBS);
        final AtomicInteger coins = new AtomicInteger(balance);
        when(clan.getId()).thenReturn(id);
        when(clan.getName()).thenReturn("Clan" + id);
        when(clan.getMembers()).thenReturn(new ArrayList<>());
        when(clan.getAlliances()).thenReturn(new ArrayList<>());
        when(clan.getEnemies()).thenReturn(new ArrayList<>());
        when(clan.getTerritory()).thenReturn(new ArrayList<>());
        when(clan.getBalance()).thenAnswer(ask -> coins.get());
        doAnswer(ask -> {
            coins.set(ask.getArgument(0));
            return null;
        }).when(clan).setBalance(anyInt());
        held.put(id, clan);
        return clan;
    }

    static @NotNull Database database(@NotNull DataSource source) {
        final IDatabaseConnection connection = new IDatabaseConnection() {
            @Override
            public void runDatabaseMigrations(ClassLoader classLoader, String location, String name) {
            }

            @Override
            public DataSource getDataSource() {
                return source;
            }
        };
        final Database database = mock(Database.class);
        when(database.getDslContext()).thenReturn(DSL.using(source, SQLDialect.POSTGRES));
        when(database.getAsyncDslContext()).thenReturn(new AsyncDSLContext(connection, Executors.newCachedThreadPool()));
        return database;
    }

    static @NotNull LedgerBalance balance(@NotNull AtomicInteger holder) {
        return new LedgerBalance() {
            @Override
            public long get() {
                return holder.get();
            }

            @Override
            public void set(long balance) {
                holder.set((int) balance);
            }
        };
    }
}
