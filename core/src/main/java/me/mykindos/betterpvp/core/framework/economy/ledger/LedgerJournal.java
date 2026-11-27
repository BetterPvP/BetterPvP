package me.mykindos.betterpvp.core.framework.economy.ledger;

import me.mykindos.betterpvp.ledger.LedgerPosting;
import org.jetbrains.annotations.NotNull;

/** Keeps a record of what an in-process ledger posted. */
public interface LedgerJournal {

    void record(@NotNull LedgerPosting posting);
}
