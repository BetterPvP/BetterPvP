package me.mykindos.betterpvp.core.framework.agones;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import lombok.Getter;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.config.Config;
import me.mykindos.betterpvp.core.framework.net.NetworkLayer;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.scheduler.BukkitTask;
import sun.misc.Signal;

import java.util.ArrayList;

/**
 * Empties the server before it stops: refuses arrivals, lets features save, moves players off, then tells Agones it
 * is done and stops. Runs on SIGTERM when an Agones sidecar is present, or when {@link #drain()} is called.
 */
@BPvPListener
@Singleton
@CustomLog
public class ServerDrain implements Listener {

    private final Core core;
    private final AgonesLifecycle lifecycle;
    private final GameServerSdk sdk;
    private final NetworkLayer network;

    @Inject
    @Config(path = "core.agones.drainTarget", defaultValue = "hub")
    private String drainTarget;

    @Inject
    @Config(path = "core.agones.drainSeconds", defaultValue = "30")
    private int drainSeconds;

    @Getter
    private volatile boolean draining;
    private long deadline;
    private BukkitTask waiting;

    @Inject
    public ServerDrain(Core core, AgonesLifecycle lifecycle, GameServerSdk sdk, NetworkLayer network) {
        this.core = core;
        this.lifecycle = lifecycle;
        this.sdk = sdk;
        this.network = network;
    }

    /**
     * Takes over SIGTERM so the server keeps ticking while it empties, rather than stopping at once. Kubernetes
     * sends it when a pod is deleted, and kills the pod when its grace period runs out.
     */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (!sdk.isEnabled() || event.getType() != ServerLoadEvent.LoadType.STARTUP) {
            return;
        }

        try {
            Signal.handle(new Signal("TERM"), signal -> UtilServer.runTask(core, this::drain));
        } catch (IllegalArgumentException exception) {
            log.warn("Could not take over SIGTERM, the server will stop without draining").submit();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArrival(AsyncPlayerPreLoginEvent event) {
        if (draining) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Translations.component("core.server.draining").color(NamedTextColor.RED));
        }
    }

    /**
     * Starts emptying the server. Call on the main thread. Calling again while draining does nothing.
     */
    public void drain() {
        if (draining) {
            return;
        }
        draining = true;
        log.info("Draining: refusing arrivals and moving {} players to {}", Bukkit.getOnlinePlayers().size(),
                drainTarget).submit();

        Bukkit.getPluginManager().callEvent(new ServerDrainEvent());
        Bukkit.savePlayers();
        for (World world : Bukkit.getWorlds()) {
            world.save();
        }

        final ArrayList<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (!players.isEmpty()) {
            network.transfer().transferAll(players, drainTarget);
        }

        deadline = System.currentTimeMillis() + drainSeconds * 1000L;
        waiting = Bukkit.getScheduler().runTaskTimer(core, this::checkEmpty, 20L, 20L);
    }

    void checkEmpty() {
        final int left = Bukkit.getOnlinePlayers().size();
        if (left > 0 && System.currentTimeMillis() < deadline) {
            return;
        }

        if (left > 0) {
            log.warn("Drain timed out with {} players still online", left).submit();
        }
        waiting.cancel();
        lifecycle.shutdown();
        Bukkit.shutdown();
    }

}
