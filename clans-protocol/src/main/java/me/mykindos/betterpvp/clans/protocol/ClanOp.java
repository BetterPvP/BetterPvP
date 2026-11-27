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
    CAMP_PERMISSION
}
