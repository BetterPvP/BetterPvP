package me.mykindos.betterpvp.clans.protocol;

/** The bus topics the Clans service answers and announces on. */
public final class ClanTopics {

    /** A request carrying one {@link ClanMutation}, answered with a {@link ClanReply}. */
    public static final String MUTATE = "clans.mutate";

    /** Published once per applied mutation as a {@link ClanChange}. Servers re-read the named clans from Postgres. */
    public static final String CHANGED = "clans.changed";

    private ClanTopics() {
    }
}
