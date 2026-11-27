package me.mykindos.betterpvp.clans.clans.writer;

import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import org.jetbrains.annotations.NotNull;

/**
 * The only way a clan's membership, ranks, relations and camp permissions are written.
 * <p>
 * Callers change their in-memory {@link Clan} first, as they always have, then tell the writer. Each method persists
 * what the caller just did. Nothing here sends messages to players or fires events.
 */
public interface ClanWriter {

    /** Persists a new clan together with the leader already in its member list. */
    void create(@NotNull Clan clan);

    void disband(@NotNull Clan clan);

    void rename(@NotNull Clan clan);

    void addMember(@NotNull Clan clan, @NotNull ClanMember member);

    void removeMember(@NotNull Clan clan, @NotNull ClanMember member);

    /** Persists a promotion by one rank, which the caller has already applied to {@code member}. */
    void promote(@NotNull Clan clan, @NotNull ClanMember member);

    /** Persists a demotion by one rank, which the caller has already applied to {@code member}. */
    void demote(@NotNull Clan clan, @NotNull ClanMember member);

    /** Persists both sides of a new alliance. */
    void ally(@NotNull Clan clan, @NotNull Clan other);

    void removeAlliance(@NotNull Clan clan, @NotNull Clan other);

    void setTrust(@NotNull Clan clan, @NotNull Clan other, boolean trusted);

    /** Persists both sides of a new enmity. */
    void enemy(@NotNull Clan clan, @NotNull Clan other);

    void removeEnemy(@NotNull Clan clan, @NotNull Clan other);

    /**
     * Changes one of a camp's permissions. Unlike the other methods, this is the change itself: the in-memory camp is
     * updated by the writer, not the caller.
     */
    void setCampPermission(long clanId, @NotNull CampGrant grant);
}
