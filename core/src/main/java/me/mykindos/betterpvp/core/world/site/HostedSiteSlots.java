package me.mykindos.betterpvp.core.world.site;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.framework.agones.CampSlotsChangedEvent;
import me.mykindos.betterpvp.core.framework.agones.ServerDrain;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Reports how many owned sites this server has open as {@link CampSlotsChangedEvent}, whenever that changes.
 * <p>
 * A dormant one is not counted, since its world is closed. While the server drains it reports no room at all, so
 * nothing new is sent to it.
 */
@BPvPListener
@Singleton
public class HostedSiteSlots implements Listener {

    private final SiteInstances instances;
    private final ServerDrain drain;
    private int reportedHosted = -1;
    private boolean reportedDraining;

    @Inject
    public HostedSiteSlots(@NotNull SiteInstances instances, @NotNull ServerDrain drain) {
        this.instances = instances;
        this.drain = drain;
    }

    @UpdateEvent(delay = 1000)
    public void report() {
        final int hosted = (int) instances.all().stream()
                .filter(instance -> instance.getKey().isOwned())
                .filter(instance -> instance.getState() != SiteInstance.State.DORMANT)
                .count();
        final boolean draining = drain.isDraining();
        if (hosted == reportedHosted && draining == reportedDraining) {
            return;
        }

        reportedHosted = hosted;
        reportedDraining = draining;
        // A capacity below zero keeps the configured number of slots.
        UtilServer.callEvent(new CampSlotsChangedEvent(hosted, draining ? hosted : -1));
    }
}
