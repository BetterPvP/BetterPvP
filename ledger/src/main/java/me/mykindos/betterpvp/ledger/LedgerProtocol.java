package me.mykindos.betterpvp.ledger;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ledger's requests and replies as flat string maps, the payload every message bus carries.
 */
public final class LedgerProtocol {

    /** Posts a {@link LedgerPosting}. Answers a {@link LedgerReceipt}. */
    public static final String POST = "ledger.post";

    /**
     * Opens a player account with an opening balance if it has none, and answers a {@link LedgerReceipt} holding its
     * balance either way. The opening balance is how a server's existing balances move into the ledger the first
     * time it sees a player.
     */
    public static final String OPEN = "ledger.open";

    private static final String COUNT = "count";
    private static final String REFERENCE = "reference";
    private static final String STATUS = "status";
    private static final String SEQUENCE = "sequence";
    private static final String ACCOUNT = "account";
    private static final String OPENING = "opening";
    private static final String BALANCE_PREFIX = "balance.";

    private LedgerProtocol() {
    }

    public static @NotNull Map<String, String> encode(@NotNull LedgerPosting posting) {
        final Map<String, String> map = new HashMap<>();
        final List<LedgerEntry> entries = posting.getEntries();
        map.put(COUNT, Integer.toString(entries.size()));
        for (int i = 0; i < entries.size(); i++) {
            final LedgerEntry entry = entries.get(i);
            map.put(i + ".from", entry.getFrom());
            map.put(i + ".to", entry.getTo());
            map.put(i + ".amount", Long.toString(entry.getAmount()));
            map.put(i + ".reason", entry.getReason());
        }
        if (posting.getReference() != null) {
            map.put(REFERENCE, posting.getReference());
        }
        return map;
    }

    /** @throws IllegalArgumentException if the map is not a posting */
    public static @NotNull LedgerPosting decodePosting(@NotNull Map<String, String> map) {
        final int count = Integer.parseInt(required(map, COUNT));
        final List<LedgerEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new LedgerEntry(required(map, i + ".from"), required(map, i + ".to"),
                    Long.parseLong(required(map, i + ".amount")), required(map, i + ".reason")));
        }
        return new LedgerPosting(List.copyOf(entries), map.get(REFERENCE));
    }

    public static @NotNull Map<String, String> encodeOpen(@NotNull String account, long opening) {
        final Map<String, String> map = new HashMap<>();
        map.put(ACCOUNT, account);
        map.put(OPENING, Long.toString(opening));
        return map;
    }

    public static @NotNull String openAccount(@NotNull Map<String, String> map) {
        return required(map, ACCOUNT);
    }

    public static long openingBalance(@NotNull Map<String, String> map) {
        return Long.parseLong(required(map, OPENING));
    }

    public static @NotNull Map<String, String> encode(@NotNull LedgerReceipt receipt) {
        final Map<String, String> map = new HashMap<>();
        map.put(STATUS, receipt.getStatus().name());
        map.put(SEQUENCE, Long.toString(receipt.getSequence()));
        receipt.getBalances().forEach((account, balance) -> map.put(BALANCE_PREFIX + account, Long.toString(balance)));
        return map;
    }

    /** @throws IllegalArgumentException if the map is not a receipt */
    public static @NotNull LedgerReceipt decodeReceipt(@NotNull Map<String, String> map) {
        final LedgerStatus status = LedgerStatus.valueOf(required(map, STATUS));
        final Map<String, Long> balances = new HashMap<>();
        map.forEach((key, value) -> {
            if (key.startsWith(BALANCE_PREFIX)) {
                balances.put(key.substring(BALANCE_PREFIX.length()), Long.parseLong(value));
            }
        });
        return new LedgerReceipt(status, Map.copyOf(balances), Long.parseLong(map.getOrDefault(SEQUENCE, "0")));
    }

    private static @NotNull String required(@NotNull Map<String, String> map, @NotNull String key) {
        final String value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing " + key);
        }
        return value;
    }
}
