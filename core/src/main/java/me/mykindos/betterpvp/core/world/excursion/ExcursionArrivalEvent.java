package me.mykindos.betterpvp.core.world.excursion;

import lombok.EqualsAndHashCode;
import lombok.Value;
import me.mykindos.betterpvp.core.framework.events.CustomEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A party sent here by {@link Excursions#depart} is in. Fires once, on the main thread, when every member has joined or
 * when the wait for stragglers runs out, so {@link #getTravellers()} can be fewer than the members.
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ExcursionArrivalEvent extends CustomEvent {

    @NotNull Excursion excursion;
    @NotNull List<Player> travellers;
}
