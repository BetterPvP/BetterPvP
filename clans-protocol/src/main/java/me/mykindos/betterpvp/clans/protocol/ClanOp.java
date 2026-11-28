package me.mykindos.betterpvp.clans.protocol;

/** Every change the Clans service applies. Travels by its name. */
public enum ClanOp {
    CREATE,
    DISBAND,
    RENAME,
    MEMBER_ADD,
    MEMBER_REMOVE,
    MEMBER_PROMOTE,
    MEMBER_DEMOTE,
    ALLY,
    UNALLY,
    TRUST,
    ENEMY,
    UNENEMY,
    CAMP_PERMISSION,
    /** Sets the clan properties named in {@link ClanMutation#getProperties()}. */
    PROPERTIES,
    /** Moves the clan core to {@link ClanMutation#getValue()}, or removes it when that is empty. */
    CORE,
    BANNER,
    VAULT,
    MAILBOX,
    /** Claims the chunk named in {@link ClanMutation#getValue()}. */
    CLAIM,
    UNCLAIM,
    /** Sets the clan's dominance over {@link ClanMutation#getOther()} to {@link ClanMutation#getValue()}. */
    DOMINANCE,
    /**
     * The clan killed a member of {@link ClanMutation#getOther()} for {@link ClanMutation#getValue()} dominance. The
     * other clan's dominance over this one falls by that much, and this one's rises by it only when the other's was
     * already none. Both stay between 0 and 100.
     */
    DOMINANCE_KILL
}
