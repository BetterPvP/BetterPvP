package me.mykindos.betterpvp.orchestration.service.placement;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.Nats;
import io.nats.client.Options;
import lombok.Getter;
import lombok.extern.java.Log;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/** Connects Placement to NATS and Kubernetes and starts answering. */
@Log
public final class PlacementService implements AutoCloseable {

    @Getter
    private final Connection connection;
    private final PlacementResponder responder;

    private PlacementService(Connection connection, PlacementResponder responder) {
        this.connection = connection;
        this.responder = responder;
    }

    public static PlacementService start(PlacementConfig config) throws IOException {
        final AtomicReference<Placer> placer = new AtomicReference<>();
        final Options.Builder options = Options.builder()
                .server(config.getNatsUrl())
                .connectionName("placement")
                .maxReconnects(-1)
                .connectionListener((conn, event) -> {
                    if (event == ConnectionListener.Events.RECONNECTED && placer.get() != null) {
                        placer.get().markReachable();
                    }
                });
        if (!config.getNatsToken().isBlank()) {
            options.token(config.getNatsToken().toCharArray());
        }

        final Connection connection;
        try {
            connection = Nats.connect(options.build());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted connecting to NATS", interrupted);
        }

        final AgonesServerPool pool = new AgonesServerPool(httpClient(config), config.getKubernetesUrl(),
                namespace(config), () -> readToken(config.getTokenFile()), config.getFleets());
        placer.set(new Placer(NatsLeaseStore.open(connection, config.getPrefix(), config.getReplicas()), pool,
                config::roleOf, config.getLeaseTtl(), System::currentTimeMillis));

        final PlacementResponder responder = new PlacementResponder(connection, placer.get(), "placement");
        responder.start(config.getPrefix());
        log.info("Placement is answering on NATS at " + config.getNatsUrl());
        return new PlacementService(connection, responder);
    }

    @Override
    public void close() {
        responder.close();
        try {
            connection.close();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String namespace(PlacementConfig config) throws IOException {
        if (!config.getNamespace().isBlank()) {
            return config.getNamespace();
        }
        final Path mounted = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
        if (Files.exists(mounted)) {
            return Files.readString(mounted).trim();
        }
        return "default";
    }

    private static String readToken(String tokenFile) {
        try {
            final Path path = Path.of(tokenFile);
            return Files.exists(path) ? Files.readString(path) : null;
        } catch (IOException exception) {
            log.warning("Could not read the Kubernetes token at " + tokenFile + ": " + exception.getMessage());
            return null;
        }
    }

    /** Trusts the cluster's own certificate authority when it is mounted, and the JVM's otherwise. */
    private static HttpClient httpClient(PlacementConfig config) throws IOException {
        final HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5));
        final Path ca = Path.of(config.getCaFile());
        if (!Files.exists(ca)) {
            return builder.build();
        }

        try (InputStream in = Files.newInputStream(ca)) {
            final KeyStore trusted = KeyStore.getInstance(KeyStore.getDefaultType());
            trusted.load(null, null);
            int index = 0;
            for (Certificate certificate : CertificateFactory.getInstance("X.509").generateCertificates(in)) {
                trusted.setCertificateEntry("cluster-" + index++, certificate);
            }
            final TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(trusted);
            final SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, trust.getTrustManagers(), null);
            return builder.sslContext(ssl).build();
        } catch (GeneralSecurityException exception) {
            throw new IOException("Could not trust the cluster certificate at " + ca, exception);
        }
    }
}
