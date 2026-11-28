package me.mykindos.betterpvp.core.framework.economy.ledger;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.gamer.properties.GamerProperty;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One side of a movement: a held account such as a player's, whose cached balance follows the ledger, or a system
 * account.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerParty {

    @NotNull String account;
    /** Null for a system account. */
    @Nullable LedgerBalance balance;

    public static @NotNull LedgerParty of(@NotNull Gamer gamer) {
        return held(LedgerAccounts.player(gamer.getUniqueId()), new LedgerBalance() {
            @Override
            public long get() {
                return gamer.getBalance();
            }

            @Override
            public void set(long balance) {
                gamer.saveProperty(GamerProperty.BALANCE, (int) Math.min(Integer.MAX_VALUE, balance));
            }
        });
    }

    /** A player or clan account from {@link LedgerAccounts}, cached on this server in {@code balance}. */
    public static @NotNull LedgerParty held(@NotNull String account, @NotNull LedgerBalance balance) {
        return new LedgerParty(account, balance);
    }

    /** One of {@link LedgerAccounts}' system accounts. */
    public static @NotNull LedgerParty system(@NotNull String account) {
        return new LedgerParty(account, null);
    }
}
