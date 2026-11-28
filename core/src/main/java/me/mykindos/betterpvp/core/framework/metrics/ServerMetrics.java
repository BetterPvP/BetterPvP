package me.mykindos.betterpvp.core.framework.metrics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.CustomLog;
import lombok.Getter;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.config.Config;
import me.mykindos.betterpvp.core.framework.role.RoleGate;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.metrics.MetricsExporter;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;

/**
 * Serves this server's players, tick time and heap after GC to Prometheus on {@code core.metrics.port}, with the
 * JVM's own meters. Every meter is tagged with the server and its role.
 */
@BPvPListener
@Singleton
@CustomLog
public class ServerMetrics implements Listener {

    private final Core core;
    private final RoleGate roleGate;

    @Inject
    @Config(path = "core.metrics.enabled", defaultValue = "false")
    private boolean enabled;

    @Inject
    @Config(path = "core.metrics.port", defaultValue = "9464")
    private int port;

    private MetricsExporter exporter;
    private volatile int players;

    /** The average milliseconds per tick over the last 100 ticks. */
    @Getter
    private volatile double mspt;
    private volatile double tps;

    @Inject
    public ServerMetrics(Core core, RoleGate roleGate) {
        this.core = core;
        this.roleGate = roleGate;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        if (!enabled || event.getType() != ServerLoadEvent.LoadType.STARTUP) {
            return;
        }

        try {
            exporter = new MetricsExporter(port, Tags.of("server", serverTag(), "role", roleGate.getRole().getId()));
        } catch (IOException ex) {
            log.error("Could not serve metrics on port {}", port, ex).submit();
            return;
        }
        final MeterRegistry registry = exporter.getRegistry();
        Gauge.builder("bpvp.server.players", this, metrics -> metrics.players)
                .description("Players online")
                .register(registry);
        Gauge.builder("bpvp.server.mspt", this, ServerMetrics::getMspt)
                .description("Average milliseconds per tick over the last 100 ticks")
                .register(registry);
        Gauge.builder("bpvp.server.tps", this, metrics -> metrics.tps)
                .description("Ticks per second over the last minute")
                .register(registry);
        Gauge.builder("bpvp.server.heap.post.gc", this, ServerMetrics::getPostGcHeapBytes)
                .description("Heap still in use after each pool's last collection")
                .baseUnit("bytes")
                .register(registry);
        log.info("Serving metrics on port {}", port).submit();
    }

    @UpdateEvent(delay = 1000)
    public void sample() {
        players = Bukkit.getOnlinePlayers().size();
        mspt = Bukkit.getAverageTickTime();
        tps = Bukkit.getTPS()[0];
    }

    /**
     * The heap still in use after each heap pool's last collection, which leaves out garbage waiting to be collected.
     */
    public long getPostGcHeapBytes() {
        long used = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            final MemoryUsage afterGc = pool.getCollectionUsage();
            if (pool.getType() == MemoryType.HEAP && afterGc != null) {
                used += afterGc.getUsed();
            }
        }
        return used;
    }

    public void close() {
        if (exporter != null) {
            exporter.close();
            exporter = null;
        }
    }

    /**
     * The {@code server} tag on every meter: the pod name.
     */
    private String serverTag() {
        final String pod = System.getenv("HOSTNAME");
        return pod == null || pod.isBlank() ? core.getConfig().getOrSaveString("core.info.server", "unknown") : pod;
    }
}
