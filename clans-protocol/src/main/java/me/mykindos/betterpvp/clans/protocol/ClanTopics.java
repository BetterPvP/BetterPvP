package me.mykindos.betterpvp.clans.protocol;

/** The bus topics the Clans service answers and announces on. */
public final class ClanTopics {

    /** A request carrying one {@link ClanMutation}, answered with a {@link ClanReply}. */
    public static final String MUTATE = "clans.mutate";

    /** Published once per applied mutation as a {@link ClanChange}. Servers re-read the named clans from Postgres. */
    public static final String CHANGED = "clans.changed";

    /**
     * Published as a {@link ClanBalance} by the server whose ledger posting moved a clan's coins. The ledger is the
     * only writer of clan balances, and this keeps every server's cached balance in step with it.
     */
    public static final String BALANCE = "clans.balance";

    private ClanTopics() {
    }
}
