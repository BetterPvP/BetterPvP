package me.mykindos.betterpvp.core.framework.economy.ledger;

/** This server's cached copy of a held account's balance, which follows what the ledger answers. */
public interface LedgerBalance {

    long get();

    void set(long balance);
}
