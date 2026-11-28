package me.mykindos.betterpvp.ledger;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/** One movement of coins from one account to another. */
@Value
public class LedgerEntry {

    @NotNull String from;
    @NotNull String to;
    long amount;
    @NotNull String reason;
}
