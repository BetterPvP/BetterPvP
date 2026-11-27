package me.mykindos.betterpvp.clans.clans.writer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.core.framework.economy.ledger.Ledger;
import me.mykindos.betterpvp.core.framework.economy.ledger.Movement;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Posts to this server's own ledger, which answers at once and keeps the clan's balance in its properties. */
@Singleton
public class LocalClanBank implements ClanBank {

    private final Ledger ledger;

    @Inject
    public LocalClanBank(@NotNull Ledger ledger) {
        this.ledger = ledger;
    }

    @Override
    public @NotNull CompletableFuture<LedgerReceipt> post(@NotNull List<Movement> movements,
                                                          @Nullable String reference) {
        return ledger.post(movements, reference);
    }

    /** The balance loaded with the clan is already the one this server's ledger keeps. */
    @Override
    public void sync(@NotNull Collection<Clan> clans) {
    }
}
