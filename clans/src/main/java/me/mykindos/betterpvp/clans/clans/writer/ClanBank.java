package me.mykindos.betterpvp.clans.clans.writer;

import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerBalance;
import me.mykindos.betterpvp.core.framework.economy.ledger.LedgerParty;
import me.mykindos.betterpvp.core.framework.economy.ledger.Movement;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The only way a clan's coins move. Every movement is a ledger posting with the clan as an account, and the clan's
 * {@code BALANCE} property is what the ledger last said it is.
 */
public interface ClanBank {

    /** Lands every movement or none, with the same meaning of {@code reference} as the ledger. */
    @NotNull CompletableFuture<LedgerReceipt> post(@NotNull List<Movement> movements, @Nullable String reference);

    default @NotNull CompletableFuture<LedgerReceipt> move(@NotNull LedgerParty from, @NotNull LedgerParty to,
                                                           long amount, @NotNull String reason) {
        return post(List.of(new Movement(from, to, amount, reason)), null);
    }

    /** Brings the balances of clans this server just loaded in line with wherever their coins are kept. */
    void sync(@NotNull Collection<Clan> clans);

    default @NotNull LedgerParty account(@NotNull Clan clan) {
        return LedgerParty.held(LedgerAccounts.clan(clan.getId()), new LedgerBalance() {
            @Override
            public long get() {
                return clan.getBalance();
            }

            @Override
            public void set(long balance) {
                clan.setBalance((int) Math.min(Integer.MAX_VALUE, balance));
            }
        });
    }
}
