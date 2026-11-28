package me.mykindos.betterpvp.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsExporterTest {

    @Test
    void servesTaggedMetersInPrometheusFormat() throws Exception {
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        try (MetricsExporter exporter = new MetricsExporter(port, Tags.of("server", "camp-1"))) {
            Gauge.builder("bpvp.server.players", () -> 3).register(exporter.getRegistry());

            final HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/metrics")).build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("bpvp_server_players{server=\"camp-1\"} 3.0"), response.body());
            assertTrue(response.body().contains("jvm_memory_used_bytes{"));
        }
    }
}
