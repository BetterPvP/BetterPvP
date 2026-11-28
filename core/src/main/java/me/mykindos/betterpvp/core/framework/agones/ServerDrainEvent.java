package me.mykindos.betterpvp.core.framework.agones;

import lombok.Getter;
import me.mykindos.betterpvp.core.framework.events.CustomEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Called on the main thread when the server starts draining, after it stops accepting arrivals and before players
 * are moved off. Save anything that must outlive the server here.
 * <p>
 * Every player still online is then sent to the {@link #getDrainTarget() drain target}, except those a listener has
 * {@link #claim claimed} to move somewhere itself.
 */
public class ServerDrainEvent extends CustomEvent {

    /** Where unclaimed players are sent. */
    @Getter
    private final @NotNull String drainTarget;
    private final Set<UUID> claimed = new HashSet<>();

    public ServerDrainEvent(@NotNull String drainTarget) {
        this.drainTarget = drainTarget;
    }

    /** Takes a player off the move to the drain target, for a listener that moves them itself. */
    public void claim(@NotNull Player player) {
        claimed.add(player.getUniqueId());
    }

    public boolean isClaimed(@NotNull Player player) {
        return claimed.contains(player.getUniqueId());
    }
}
