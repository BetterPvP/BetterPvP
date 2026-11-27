package me.mykindos.betterpvp.clans.world.camp;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.config.Config;
import me.mykindos.betterpvp.core.framework.agones.AgonesLifecycle;
import me.mykindos.betterpvp.core.framework.role.RoleGate;
import me.mykindos.betterpvp.core.framework.role.ServerRole;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Sets a camp server's player limit to room for a full clan and its visitors in every camp it holds. Every other role
 * keeps the limit its server is configured with.
 */
@BPvPListener
@Singleton
public class CampServerLimit implements Listener {

    private final AgonesLifecycle agones;
    private final RoleGate roleGate;

    @Inject
    @Config(path = "clans.members.max", defaultValue = "8")
    private int clanSize;

    /** Players outside the owning clan that a camp has room for at once. */
    @Inject
    @Config(path = "clans.camp.visitors", defaultValue = "4")
    private int visitors;

    @Inject
    public CampServerLimit(@NotNull AgonesLifecycle agones, @NotNull RoleGate roleGate) {
        this.agones = agones;
        this.roleGate = roleGate;
    }

    @EventHandler
    public void onServerLoad(@NotNull ServerLoadEvent event) {
        if (roleGate.getRole() == ServerRole.CAMP) {
            Bukkit.setMaxPlayers(limit());
        }
    }

    /** Camp slots times the players one camp has room for. */
    public int limit() {
        return agones.getCampSlots() * (clanSize + visitors);
    }
}
