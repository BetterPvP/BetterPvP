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
    DOMINANCE
}
