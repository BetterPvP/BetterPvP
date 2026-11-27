package me.mykindos.betterpvp.core.framework.net;

import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.config.ExtendedYamlConfiguration;
import me.mykindos.betterpvp.core.logging.LogAppender;
import me.mykindos.betterpvp.core.logging.LoggerFactory;
import me.mykindos.betterpvp.core.logging.PendingLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Choosing what carries the network")
class NetworkLayerTest {

    private final Map<String, String> config = new HashMap<>();
    private final Map<String, Object> settings = new HashMap<>();

    private NetworkTransports transports;
    private NetworkLayer layer;

    @BeforeEach
    void setUp() {
        final ExtendedYamlConfiguration yaml = mock(ExtendedYamlConfiguration.class);
        when(yaml.getOrSaveString(anyString(), anyString()))
                .thenAnswer(call -> config.getOrDefault(call.getArgument(0, String.class),
                        call.getArgument(1, String.class)));

        when(yaml.getOrSaveObject(anyString(), any(), eq(Boolean.class)))
                .thenAnswer(call -> settings.getOrDefault(call.getArgument(0, String.class), call.getArgument(1)));
        when(yaml.getOrSaveObject(anyString(), any(), eq(Integer.class)))
                .thenAnswer(call -> settings.getOrDefault(call.getArgument(0, String.class), call.getArgument(1)));

        final Core core = mock(Core.class);
        when(core.getConfig()).thenReturn(yaml);

        // Redis says it is not configured, which is the shape every unavailable implementation takes.
        final RedisConnection redis = mock(RedisConnection.class);
        when(redis.isUsable()).thenReturn(false);

        transports = new NetworkTransports();
        layer = new NetworkLayer(core, transports, redis, new NatsConnection(core),
                () -> mock(VelocityMessageBus.class),
                LocalSiteDirectory::new,
                () -> mock(ProxyPlayerTransfer.class));
    }

    @Test
    @DisplayName("with nothing configured, each seam takes the best that can run")
    void autoTakesWhatIsAvailable() {
        assertTrue(layer.directory() instanceof LocalSiteDirectory, "Redis is not usable, so the local one is left");
        assertTrue(layer.bus() instanceof VelocityMessageBus);
    }

    @Test
    @DisplayName("another network's implementation is used when config names it")
    void aRegisteredNameWins() {
        final MessageBus theirs = mock(MessageBus.class);
        final PlayerTransfer theirTransfer = mock(PlayerTransfer.class);
        transports.registerBus("theirs", () -> theirs);
        transports.registerTransfer("theirs", () -> theirTransfer);
        config.put("core.network.bus", "theirs");
        config.put("core.network.transfer", "theirs");

        assertSame(theirs, layer.bus());
        assertSame(theirTransfer, layer.transfer());
    }

    @Test
    @DisplayName("a name is matched however it was written")
    void namesAreMatchedLoosely() {
        final MessageBus theirs = mock(MessageBus.class);
        transports.registerBus("TheirBus", () -> theirs);
        config.put("core.network.bus", "  theirbus ");

        assertSame(theirs, layer.bus());
    }

    @Test
    @DisplayName("a name nobody registered falls back rather than leaving the server without a bus")
    void anUnknownNameFallsBack() {
        config.put("core.network.bus", "nothing-registered-this");

        assertTrue(layer.bus() instanceof VelocityMessageBus);
    }

    @Test
    @DisplayName("an implementation that cannot run here is passed over")
    void anUnavailableImplementationIsPassedOver() {
        transports.registerBus("theirs", () -> null);
        config.put("core.network.bus", "theirs");

        assertTrue(layer.bus() instanceof VelocityMessageBus);
    }

    @Test
    @DisplayName("each seam is resolved once and kept")
    void seamsAreResolvedOnce() {
        assertSame(layer.bus(), layer.bus());
        assertSame(layer.directory(), layer.directory());
        assertSame(layer.transfer(), layer.transfer());
    }

    @Test
    @DisplayName("the seams are chosen separately, so one can be replaced without the others")
    void seamsAreIndependent() {
        final PlayerTransfer theirTransfer = mock(PlayerTransfer.class);
        transports.registerTransfer("theirs", () -> theirTransfer);
        config.put("core.network.transfer", "theirs");

        assertSame(theirTransfer, layer.transfer());
        assertTrue(layer.bus() instanceof VelocityMessageBus, "the bus should be untouched");
        assertTrue(layer.directory() instanceof LocalSiteDirectory, "the directory should be untouched");
    }

    @Test
    @DisplayName("a directory only this server can read does not claim to be shared")
    void localDirectoryIsNotShared() {
        assertTrue(layer.directory().isAvailable());
        assertSame(false, layer.directory().isShared());
    }

    @Test
    @DisplayName("naming one seam does not name the others")
    void namingOneSeamLeavesTheRest() {
        final SiteDirectory theirs = mock(SiteDirectory.class);
        transports.registerDirectory("theirs", () -> theirs);
        config.put("core.network.directory", "theirs");

        assertSame(theirs, layer.directory());
        assertTrue(layer.bus() instanceof VelocityMessageBus);
    }

    @Test
    @DisplayName("a server told to use NATS that cannot reach it warns and falls back")
    void unreachableNatsFallsBack() throws InterruptedException {
        final List<PendingLog> warnings = new CopyOnWriteArrayList<>();
        final LogAppender appender = log -> {
            if ("WARN".equalsIgnoreCase(log.getLevel())) {
                warnings.add(log);
            }
        };
        LoggerFactory.getInstance().addAppender(appender);

        try {
            settings.put("core.nats.enabled", true);
            config.put("core.nats.url", "nats://127.0.0.1:1");
            config.put("core.network.bus", "nats");
            config.put("core.network.directory", "nats");

            assertTrue(layer.bus() instanceof VelocityMessageBus);
            assertTrue(layer.directory() instanceof LocalSiteDirectory);

            final long deadline = System.currentTimeMillis() + 2_000;
            while (warnings.stream().noneMatch(log -> log.getMessage().contains("falling back"))
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue(warnings.stream().anyMatch(log -> log.getMessage().contains("falling back")),
                    "the fallback should be said out loud");
        } finally {
            LoggerFactory.getInstance().removeAppender(appender);
        }
    }

    @Test
    @DisplayName("NATS left unconfigured is passed over without trying to connect")
    void unconfiguredNatsIsPassedOver() {
        assertTrue(layer.bus() instanceof VelocityMessageBus);
        assertTrue(layer.directory() instanceof LocalSiteDirectory);
    }

    @Test
    @DisplayName("without NATS there is no Placement service, so hosts come from the directory as before")
    void hostsFallBackToTheDirectory() {
        assertTrue(layer.hosts() instanceof DirectorySiteHosts);
    }
}
