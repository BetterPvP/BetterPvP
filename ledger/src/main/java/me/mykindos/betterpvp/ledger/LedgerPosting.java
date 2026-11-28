package me.mykindos.betterpvp.ledger;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Entries that land together or not at all.
 * <p>
 * A posting with a reference lands at most once. Sending the same entries under the same reference again answers
 * {@link LedgerStatus#POSTED} without moving anything, so a sender may retry. Different entries under a reference
 * that already landed answer {@link LedgerStatus#DUPLICATE}.
 */
@Value
public class LedgerPosting {

    @NotNull List<LedgerEntry> entries;
    @Nullable String reference;

    public static @NotNull LedgerPosting of(@NotNull LedgerEntry entry) {
        return new LedgerPosting(List.of(entry), null);
    }

    /** Identifies the entries, so a retry of the same posting can be told from a different one. */
    public @NotNull String fingerprint() {
        final StringBuilder builder = new StringBuilder();
        for (LedgerEntry entry : entries) {
            builder.append(entry.getFrom()).append('>').append(entry.getTo()).append(':').append(entry.getAmount())
                    .append(';');
        }
        return builder.toString();
    }

    /** Why the posting can never land, or {@code null} if it is well formed. */
    public @Nullable String problem() {
        if (entries.isEmpty()) {
            return "no entries";
        }
        for (LedgerEntry entry : entries) {
            if (entry.getAmount() <= 0) {
                return "amount must be positive";
            }
            if (entry.getFrom().equals(entry.getTo())) {
                return "from and to are the same account";
            }
        }
        return null;
    }
}
