package me.mykindos.betterpvp.ledger;

/** How a posting ended. */
public enum LedgerStatus {

    /** Every entry landed. */
    POSTED,

    /** A player or clan account would have gone below zero, so nothing landed. */
    INSUFFICIENT_FUNDS,

    /** Other entries already landed under the same reference, so nothing landed. */
    DUPLICATE,

    /** The posting was malformed, so nothing landed. */
    INVALID,

    /** The ledger could not be reached. Nothing is known to have landed. */
    UNAVAILABLE;

    public boolean isPosted() {
        return this == POSTED;
    }
}
