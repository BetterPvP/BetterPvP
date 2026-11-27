package me.mykindos.betterpvp.core.framework.agones;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A local HTTP server standing in for the Agones SDK sidecar. Records each call as {@code METHOD /path body}.
 */
final class FakeSidecar implements AutoCloseable {

    private final HttpServer server;
    private final BlockingQueue<String> calls = new LinkedBlockingQueue<>();

    FakeSidecar() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " " + body);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    URI base() {
        return URI.create("http://localhost:" + server.getAddress().getPort() + "/");
    }

    /**
     * Every call that arrives within the wait.
     */
    List<String> callsWithin(long millis) throws InterruptedException {
        final List<String> received = new ArrayList<>();
        final long until = System.currentTimeMillis() + millis;
        long left = millis;
        while (left > 0) {
            final String call = calls.poll(left, TimeUnit.MILLISECONDS);
            if (call != null) {
                received.add(call);
            }
            left = until - System.currentTimeMillis();
        }
        return received;
    }

    @Override
    public void close() {
        server.stop(0);
    }

}
