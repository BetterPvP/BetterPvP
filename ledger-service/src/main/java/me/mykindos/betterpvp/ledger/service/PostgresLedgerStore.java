package me.mykindos.betterpvp.ledger.service;

import me.mykindos.betterpvp.ledger.LedgerAccounts;
import me.mykindos.betterpvp.ledger.LedgerEntry;
import me.mykindos.betterpvp.ledger.LedgerPosting;
import me.mykindos.betterpvp.ledger.LedgerReceipt;
import me.mykindos.betterpvp.ledger.LedgerStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Balances and entries in Postgres.
 * <p>
 * A posting locks every account it touches, in name order, before it reads a balance, so two postings that share an
 * account run one after the other and neither reads a balance the other is about to change. The posting row is
 * inserted after the locks are held, which keeps its id, used as the sequence, growing for every account.
 */
public class PostgresLedgerStore implements LedgerStore {

    private static final int ATTEMPTS = 5;
    private static final String DEADLOCK = "40P01";
    private static final String SERIALIZATION_FAILURE = "40001";

    private final DataSource dataSource;

    public PostgresLedgerStore(@NotNull DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public @NotNull LedgerReceipt post(@NotNull LedgerPosting posting) {
        if (posting.problem() != null) {
            return LedgerReceipt.of(LedgerStatus.INVALID);
        }
        return transaction(connection -> post(connection, posting));
    }

    @Override
    public @NotNull LedgerReceipt open(@NotNull String account, long opening) {
        if (!LedgerAccounts.isPlayer(account) || opening < 0) {
            return LedgerReceipt.of(LedgerStatus.INVALID);
        }
        return transaction(connection -> {
            final boolean created;
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO ledger_account (id) VALUES (?) ON CONFLICT DO NOTHING")) {
                insert.setString(1, account);
                created = insert.executeUpdate() == 1;
            }
            if (created && opening > 0) {
                return post(connection, new LedgerPosting(
                        List.of(new LedgerEntry(LedgerAccounts.OPENING, account, opening, "opening")),
                        "open:" + account));
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT balance, sequence FROM ledger_account WHERE id = ?")) {
                select.setString(1, account);
                try (ResultSet row = select.executeQuery()) {
                    row.next();
                    return new LedgerReceipt(LedgerStatus.POSTED, Map.of(account, row.getLong(1)), row.getLong(2));
                }
            }
        });
    }

    @Override
    public long balance(@NotNull String account) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT balance FROM ledger_account WHERE id = ?")) {
            select.setString(1, account);
            try (ResultSet row = select.executeQuery()) {
                return row.next() ? row.getLong(1) : 0;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private @NotNull LedgerReceipt post(@NotNull Connection connection, @NotNull LedgerPosting posting) throws SQLException {
        final SortedSet<String> accounts = new TreeSet<>();
        for (LedgerEntry entry : posting.getEntries()) {
            accounts.add(entry.getFrom());
            accounts.add(entry.getTo());
        }
        ensureAccounts(connection, accounts);

        final Map<String, Long> balances = new HashMap<>();
        long sequence = lock(connection, accounts, balances);

        final Long postingId = insertPosting(connection, posting);
        if (postingId == null) {
            final boolean sameEntries = posting.fingerprint().equals(fingerprint(connection, posting.getReference()));
            return sameEntries
                    ? new LedgerReceipt(LedgerStatus.POSTED, players(balances), sequence)
                    : LedgerReceipt.of(LedgerStatus.DUPLICATE);
        }

        for (LedgerEntry entry : posting.getEntries()) {
            balances.merge(entry.getFrom(), -entry.getAmount(), Long::sum);
            balances.merge(entry.getTo(), entry.getAmount(), Long::sum);
        }
        for (Map.Entry<String, Long> balance : balances.entrySet()) {
            if (LedgerAccounts.isPlayer(balance.getKey()) && balance.getValue() < 0) {
                return LedgerReceipt.of(LedgerStatus.INSUFFICIENT_FUNDS);
            }
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO ledger_entry (posting_id, from_account, to_account, amount, reason) VALUES (?, ?, ?, ?, ?)")) {
            for (LedgerEntry entry : posting.getEntries()) {
                insert.setLong(1, postingId);
                insert.setString(2, entry.getFrom());
                insert.setString(3, entry.getTo());
                insert.setLong(4, entry.getAmount());
                insert.setString(5, entry.getReason());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE ledger_account SET balance = ?, sequence = ? WHERE id = ?")) {
            for (Map.Entry<String, Long> balance : balances.entrySet()) {
                update.setLong(1, balance.getValue());
                update.setLong(2, postingId);
                update.setString(3, balance.getKey());
                update.addBatch();
            }
            update.executeBatch();
        }
        return new LedgerReceipt(LedgerStatus.POSTED, players(balances), postingId);
    }

    private void ensureAccounts(@NotNull Connection connection, @NotNull SortedSet<String> accounts) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO ledger_account (id) VALUES (?) ON CONFLICT DO NOTHING")) {
            for (String account : accounts) {
                insert.setString(1, account);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    /** Locks the accounts, reads their balances into {@code balances} and answers the highest sequence among them. */
    private long lock(@NotNull Connection connection, @NotNull SortedSet<String> accounts,
                      @NotNull Map<String, Long> balances) throws SQLException {
        long sequence = 0;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, balance, sequence FROM ledger_account WHERE id = ANY (?) ORDER BY id FOR UPDATE")) {
            select.setArray(1, connection.createArrayOf("text", accounts.toArray()));
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    balances.put(rows.getString(1), rows.getLong(2));
                    sequence = Math.max(sequence, rows.getLong(3));
                }
            }
        }
        return sequence;
    }

    /** Answers the new posting's id, or {@code null} if its reference already landed. */
    private @Nullable Long insertPosting(@NotNull Connection connection, @NotNull LedgerPosting posting) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO ledger_posting (reference, fingerprint) VALUES (?, ?) ON CONFLICT (reference) DO NOTHING RETURNING id")) {
            insert.setString(1, posting.getReference());
            insert.setString(2, posting.fingerprint());
            try (ResultSet row = insert.executeQuery()) {
                return row.next() ? row.getLong(1) : null;
            }
        }
    }

    private @Nullable String fingerprint(@NotNull Connection connection, @Nullable String reference) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT fingerprint FROM ledger_posting WHERE reference = ?")) {
            select.setString(1, reference);
            try (ResultSet row = select.executeQuery()) {
                return row.next() ? row.getString(1) : null;
            }
        }
    }

    private static @NotNull Map<String, Long> players(@NotNull Map<String, Long> balances) {
        final Map<String, Long> players = new HashMap<>();
        balances.forEach((account, balance) -> {
            if (LedgerAccounts.isPlayer(account)) {
                players.put(account, balance);
            }
        });
        return Map.copyOf(players);
    }

    /** Runs {@code work} in a transaction, committing only what posted, and retries when Postgres picks it to abort. */
    private @NotNull LedgerReceipt transaction(@NotNull Work work) {
        for (int attempt = 1; ; attempt++) {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    final LedgerReceipt receipt = work.run(connection);
                    if (receipt.isPosted()) {
                        connection.commit();
                    } else {
                        connection.rollback();
                    }
                    return receipt;
                } catch (SQLException ex) {
                    connection.rollback();
                    throw ex;
                }
            } catch (SQLException ex) {
                final boolean retryable = DEADLOCK.equals(ex.getSQLState()) || SERIALIZATION_FAILURE.equals(ex.getSQLState());
                if (!retryable || attempt >= ATTEMPTS) {
                    throw new IllegalStateException("Ledger transaction failed", ex);
                }
            }
        }
    }

    @FunctionalInterface
    private interface Work {
        @NotNull LedgerReceipt run(@NotNull Connection connection) throws SQLException;
    }
}
