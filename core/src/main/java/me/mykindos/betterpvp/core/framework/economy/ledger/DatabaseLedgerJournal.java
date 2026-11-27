package me.mykindos.betterpvp.core.framework.economy.ledger;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.database.Database;
import me.mykindos.betterpvp.ledger.LedgerEntry;
import me.mykindos.betterpvp.ledger.LedgerPosting;
import org.jetbrains.annotations.NotNull;

/** Writes an in-process ledger's postings to the {@code ledger_entries} table. */
@Singleton
@CustomLog
public class DatabaseLedgerJournal implements LedgerJournal {

    private final Database database;

    @Inject
    public DatabaseLedgerJournal(@NotNull Database database) {
        this.database = database;
    }

    @Override
    public void record(@NotNull LedgerPosting posting) {
        database.getAsyncDslContext().executeAsyncVoid(ctx -> {
            for (LedgerEntry entry : posting.getEntries()) {
                ctx.execute("INSERT INTO ledger_entries (from_account, to_account, amount, reason, reference) VALUES (?, ?, ?, ?, ?)",
                        entry.getFrom(), entry.getTo(), entry.getAmount(), entry.getReason(), posting.getReference());
            }
        }).exceptionally(ex -> {
            log.error("Could not journal a ledger posting", ex).submit();
            return null;
        });
    }
}
