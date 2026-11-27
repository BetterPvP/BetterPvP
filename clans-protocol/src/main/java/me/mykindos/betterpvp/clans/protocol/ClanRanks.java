package me.mykindos.betterpvp.clans.protocol;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Member ranks by name, lowest first, so the service can promote and demote without the plugin's enum. */
public final class ClanRanks {

    public static final List<String> ORDER = List.of("RECRUIT", "MEMBER", "ADMIN", "LEADER");

    private ClanRanks() {
    }

    /** The rank {@code steps} above {@code rank}, held between the lowest and highest rank. */
    public static @NotNull String step(@NotNull String rank, int steps) {
        final int index = ORDER.indexOf(rank);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown rank " + rank);
        }
        return ORDER.get(Math.clamp(index + steps, 0, ORDER.size() - 1));
    }
}
