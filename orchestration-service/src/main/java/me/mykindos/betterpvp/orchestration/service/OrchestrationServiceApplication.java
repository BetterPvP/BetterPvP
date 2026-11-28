package me.mykindos.betterpvp.orchestration.service;

import com.sun.net.httpserver.HttpServer;
import me.mykindos.betterpvp.orchestration.policy.QueuePriorityPolicy;
import me.mykindos.betterpvp.orchestration.service.http.OrchestrationHttpHandler;
import me.mykindos.betterpvp.orchestration.service.placement.PlacementConfig;
import me.mykindos.betterpvp.orchestration.service.placement.PlacementService;
import org.tomlj.Toml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OrchestrationServiceApplication {

    private OrchestrationServiceApplication() {
    }

    public static void main(String[] args) throws IOException {
        final Path configPath = Path.of(System.getProperty("orchestration.config", "config.toml"));
        final OrchestrationServiceConfig config = OrchestrationServiceConfig.load(configPath);
        // Placement connects first, so a failed connect ends the process before any other thread keeps it alive.
        final PlacementConfig placementConfig = PlacementConfig.from(Toml.parse(configPath));
        final PlacementService placement = placementConfig.isEnabled() ? PlacementService.start(placementConfig) : null;

        final InMemoryOrchestrationGateway gateway = new InMemoryOrchestrationGateway(
                new QueuePriorityPolicy(
                        config.boostIntervalSeconds(),
                        config.boostAmount()
                ),
                config.reservationTtlSeconds(),
                config.minimumRegularJoinIntervalMillis(),
                new PlayerQueuePolicyResolver(config),
                new ManagedTargetResolver(config.managedTargets())
        );

        final HttpServer server = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
        server.createContext("/", new OrchestrationHttpHandler(gateway));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        final Runnable stop = () -> {
            gateway.shutdown();
            if (placement != null) {
                placement.close();
            }
            server.stop(0);
        };

        final AtomicBoolean shuttingDown = new AtomicBoolean(false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (shuttingDown.compareAndSet(false, true)) {
                stop.run();
            }
        }));

        startConsoleListener(stop, shuttingDown);

        System.out.println("Orchestration service listening on " + config.host() + ":" + config.port() + " using config " + configPath.toAbsolutePath());
    }

    private static void startConsoleListener(Runnable stop, AtomicBoolean shuttingDown) {
        final Thread consoleThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String command = line.trim();
                    if (command.equalsIgnoreCase("stop") || command.equalsIgnoreCase("exit")) {
                        if (shuttingDown.compareAndSet(false, true)) {
                            System.out.println("Stopping orchestration service");
                            stop.run();
                            System.exit(0);
                        }
                        return;
                    }
                }
            } catch (IOException ignored) {
            }
        }, "orchestration-console");
        consoleThread.setDaemon(true);
        consoleThread.start();
    }
}
