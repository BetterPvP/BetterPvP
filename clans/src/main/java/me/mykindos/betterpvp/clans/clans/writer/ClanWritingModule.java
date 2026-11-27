package me.mykindos.betterpvp.clans.clans.writer;

import com.google.inject.AbstractModule;
import me.mykindos.betterpvp.core.framework.role.ServerRole;
import org.jetbrains.annotations.NotNull;

/**
 * Chooses how clans and their coins are written. A server with role {@code all} is a network of one and writes them
 * itself. Every other role writes through the Clans service and the ledger service, eagerly so it is listening for
 * changes from the start.
 */
public class ClanWritingModule extends AbstractModule {

    private final ServerRole role;

    public ClanWritingModule(@NotNull ServerRole role) {
        this.role = role;
    }

    @Override
    protected void configure() {
        if (role == ServerRole.ALL) {
            bind(ClanWriter.class).to(LocalClanWriter.class);
            bind(ClanBank.class).to(LocalClanBank.class);
        } else {
            bind(ClanWriter.class).to(ServiceClanWriter.class).asEagerSingleton();
            bind(ClanBank.class).to(ServiceClanBank.class).asEagerSingleton();
        }
    }
}
