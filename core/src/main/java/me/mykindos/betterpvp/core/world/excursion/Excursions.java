package me.mykindos.betterpvp.core.world.excursion;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Takes a party to a server that runs a site and brings each member back to where they left from.
 * <p>
 * The destination hears about the party before it arrives, and fires {@link ExcursionArrivalEvent} once it is in.
 * When the chosen server is this one the party never leaves, and the event fires straight away.
 */
public interface Excursions {

    /**
     * Sends a party to whichever server Placement picks for {@code site}. The first traveller leads.
     *
     * @param beforeLeaving runs on the main thread once a server is found and before anyone moves, which is where a
     *                      cost is taken so nobody pays for a trip that cannot happen
     * @return false when no server could be found, in which case {@code beforeLeaving} did not run
     */
    @NotNull CompletableFuture<Boolean> depart(@NotNull Collection<Player> party, @NotNull String site,
                                               @NotNull Map<String, String> details, @NotNull Runnable beforeLeaving);

    /**
     * Sends a player who came here on an excursion back to where they left from.
     *
     * @return false when they did not come on one, so the caller puts them somewhere itself
     */
    boolean returnHome(@NotNull Player player);

    /** The excursion a player on this server came on. */
    @NotNull Optional<Excursion> visiting(@NotNull Player player);
}
