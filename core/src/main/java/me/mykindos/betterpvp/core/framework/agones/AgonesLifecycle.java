package me.mykindos.betterpvp.core.framework.agones;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.config.Config;
import me.mykindos.betterpvp.core.framework.role.RoleGate;
import me.mykindos.betterpvp.core.framework.role.ServerRole;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

import java.util.concurrent.TimeUnit;

/**
 * Tells Agones when this server is ready, that it is still alive, and how much room it has. Camp servers report
 * camps as the {@code camp-slots} counter, every other role reports players as {@code players}.
 */
@BPvPListener
@Singleton
public class AgonesLifecycle implements Listener {

    private final GameServerSdk sdk;
    private final RoleGate roleGate;

    @Inject
    @Config(path = "core.agones.campSlots", defaultValue = "10")
    private int campSlots;

    private boolean ready;
    private boolean finished;
    private int campsHosted;
    private int campCapacity = -1;
    private long publishedCount = -1;
    private long publishedCapacity = -1;

    @Inject
    public AgonesLifecycle(GameServerSdk sdk, RoleGate roleGate) {
        this.sdk = sdk;
        this.roleGate = roleGate;
    }

    /**
     * Paper calls this once every plugin has enabled and the worlds are loaded.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        if (!sdk.isEnabled() || event.getType() != ServerLoadEvent.LoadType.STARTUP) {
            return;
        }

        sdk.setLabel("role", roleGate.getRole().getId());
        publishCapacity();
        sdk.ready();
        ready = true;
    }

    @EventHandler
    public void onCampSlotsChanged(CampSlotsChangedEvent event) {
        campsHosted = event.getHosted();
        campCapacity = event.getCapacity();
        publishCapacity();
    }

    // Pinged from the main thread so a stalled server stops reporting healthy.
    @UpdateEvent(delay = 2000)
    public void heartbeat() {
        if (!ready) {
            return;
        }
        sdk.health();
        publishCapacity();
    }

    /**
     * Tells Agones this server is finished. Waits briefly, since it runs while the server stops.
     */
    public void shutdown() {
        if (!sdk.isEnabled() || finished) {
            return;
        }
        finished = true;
        ready = false;
        sdk.shutdown().completeOnTimeout(false, 2, TimeUnit.SECONDS).join();
    }

    private void publishCapacity() {
        if (!sdk.isEnabled()) {
            return;
        }

        final boolean camp = roleGate.getRole() == ServerRole.CAMP;
        final long count = camp ? campsHosted : Bukkit.getOnlinePlayers().size();
        final long capacity = camp ? (campCapacity < 0 ? campSlots : campCapacity) : Bukkit.getMaxPlayers();
        if (count == publishedCount && capacity == publishedCapacity) {
            return;
        }

        publishedCount = count;
        publishedCapacity = capacity;
        sdk.setCounter(camp ? "camp-slots" : "players", count, capacity);
    }

}
