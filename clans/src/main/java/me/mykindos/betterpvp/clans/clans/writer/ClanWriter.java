package me.mykindos.betterpvp.clans.clans.writer;

import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.core.components.clans.IClan;
import me.mykindos.betterpvp.core.components.clans.data.ClanEnemy;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import me.mykindos.betterpvp.core.utilities.UtilWorld;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * The only way a clan is written: its membership, ranks, relations, camp permissions, properties, core, banner,
 * vault, mailbox, territory and dominance. A clan's balance is the ledger's and goes through {@link ClanBank}.
 * <p>
 * Callers change their in-memory {@link Clan} first, then tell the writer. Each method persists
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

    /** Persists a property the clan already holds. Writes may wait for {@link #flushProperties(boolean)}. */
    void saveProperty(@NotNull Clan clan, @NotNull String property, @NotNull Object value);

    /** Writes every property still waiting. */
    void flushProperties(boolean async);

    /** Persists the clan core's position, or that it has none. */
    void setCore(@NotNull Clan clan);

    void setBanner(@NotNull Clan clan);

    /** Completes once the save is stored, or once it is handed off where storing it is not waited on. */
    @NotNull CompletableFuture<Void> saveVault(@NotNull Clan clan);

    /** Completes once the save is stored, or once it is handed off where storing it is not waited on. */
    @NotNull CompletableFuture<Void> saveMailbox(@NotNull Clan clan);

    /** Persists a chunk the clan just added to its territory, as {@link UtilWorld#chunkToFile} names it. */
    void claim(@NotNull Clan clan, @NotNull String chunk);

    void unclaim(@NotNull Clan clan, @NotNull String chunk);

    /** Persists the clan's dominance over one enemy. */
    void setDominance(@NotNull IClan clan, @NotNull ClanEnemy enemy);

    /**
     * Persists what a kill did to both sides' dominance, which the caller has already applied.
     *
     * @param killerEnemy the killer's side of the enmity
     * @param killedEnemy the killed clan's side of the enmity
     * @param amount      the dominance the kill was worth
     */
    void killDominance(@NotNull IClan killer, @NotNull ClanEnemy killerEnemy, @NotNull IClan killed,
                       @NotNull ClanEnemy killedEnemy, double amount);
}
