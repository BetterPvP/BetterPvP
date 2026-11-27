package me.mykindos.betterpvp.core.framework.economy.ledger;

import lombok.Value;
import me.mykindos.betterpvp.ledger.LedgerEntry;
import me.mykindos.betterpvp.ledger.LedgerPosting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Value
public class Movement {

    @NotNull LedgerParty from;
    @NotNull LedgerParty to;
    long amount;
    @NotNull String reason;

    public @NotNull LedgerEntry toEntry() {
        return new LedgerEntry(from.getAccount(), to.getAccount(), amount, reason);
    }

    static @NotNull LedgerPosting posting(@NotNull List<Movement> movements, @Nullable String reference) {
        return new LedgerPosting(movements.stream().map(Movement::toEntry).toList(), reference);
    }
}
