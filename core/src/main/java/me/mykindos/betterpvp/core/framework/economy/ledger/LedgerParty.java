package me.mykindos.betterpvp.core.framework.economy.ledger;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.ledger.LedgerAccounts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One side of a movement: a player, whose cached balance follows the ledger, or a system account. */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerParty {

    @NotNull String account;
    @Nullable Gamer gamer;

    public static @NotNull LedgerParty of(@NotNull Gamer gamer) {
        return new LedgerParty(LedgerAccounts.player(gamer.getUniqueId()), gamer);
    }

    /** One of {@link LedgerAccounts}' system accounts. */
    public static @NotNull LedgerParty system(@NotNull String account) {
        return new LedgerParty(account, null);
    }
}
