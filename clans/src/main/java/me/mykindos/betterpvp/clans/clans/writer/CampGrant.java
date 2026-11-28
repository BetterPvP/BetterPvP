package me.mykindos.betterpvp.clans.clans.writer;

import lombok.Value;
import me.mykindos.betterpvp.core.components.clans.data.ClanMember;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One camp permission switched on or off. */
@Value
public class CampGrant {

    @NotNull Scope scope;
    /** The rank it applies to, for the per-rank scopes. */
    @Nullable ClanMember.MemberRank rank;
    /** The action's enum name, for every scope but {@link Scope#ALLY_CONTAINERS}. */
    @Nullable String action;
    boolean allowed;

    public enum Scope {
        /** A construction action for one rank. */
        RANK_CONSTRUCTION,
        /** A settler action for one rank. */
        RANK_SETTLER,
        /** A construction action for allied clans. */
        ALLY_CONSTRUCTION,
        /** Whether allied clans may open containers. */
        ALLY_CONTAINERS
    }
}
