package me.mykindos.betterpvp.ledger.service;

import me.mykindos.betterpvp.ledger.LedgerPosting;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import org.jetbrains.annotations.NotNull;

/** Where balances and entries live. Each call is all or nothing. */
public interface LedgerStore {

    @NotNull LedgerReceipt post(@NotNull LedgerPosting posting);

    /** Gives {@code account} its {@code opening} balance if it has no account yet, and answers its balance. */
    @NotNull LedgerReceipt open(@NotNull String account, long opening);

    long balance(@NotNull String account);
}
