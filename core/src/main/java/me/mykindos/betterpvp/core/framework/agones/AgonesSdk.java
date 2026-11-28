package me.mykindos.betterpvp.core.framework.agones;

import com.google.gson.JsonObject;
import lombok.CustomLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Talks to the Agones SDK sidecar over its local HTTP API. Without a sidecar, every call completes with {@code false}
 * and sends nothing.
 */
@CustomLog
public final class AgonesSdk implements GameServerSdk {

    public static final String PORT_VARIABLE = "AGONES_SDK_HTTP_PORT";

    private final @Nullable URI base;
    private final HttpClient http;
    private final AtomicBoolean failing = new AtomicBoolean(false);

    private AgonesSdk(@Nullable URI base) {
        this.base = base;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    public static @NotNull AgonesSdk fromEnvironment(@NotNull Map<String, String> environment) {
        final String port = environment.get(PORT_VARIABLE);
        if (port == null || port.isBlank()) {
            log.info("No Agones sidecar ({} is unset), lifecycle reporting is off", PORT_VARIABLE).submit();
            return new AgonesSdk(null);
        }
        log.info("Reporting lifecycle to the Agones sidecar on port {}", port).submit();
        return at(URI.create("http://localhost:" + port.trim() + "/"));
    }

    public static @NotNull AgonesSdk at(@NotNull URI base) {
        return new AgonesSdk(base);
    }

    @Override
    public boolean isEnabled() {
        return base != null;
    }

    @Override
    public @NotNull CompletableFuture<Boolean> ready() {
        return send("POST", "ready", "{}");
    }

    @Override
    public @NotNull CompletableFuture<Boolean> health() {
        return send("POST", "health", "{}");
    }

    @Override
    public @NotNull CompletableFuture<Boolean> shutdown() {
        return send("POST", "shutdown", "{}");
    }

    /**
     * Sets a label on the GameServer, which Agones prefixes with {@code agones.dev/sdk-}.
     */
    @Override
    public @NotNull CompletableFuture<Boolean> setLabel(@NotNull String key, @NotNull String value) {
        final JsonObject body = new JsonObject();
        body.addProperty("key", key);
        body.addProperty("value", value);
        return send("PUT", "metadata/label", body.toString());
    }

    /**
     * Sets a counter declared in the GameServer spec.
     */
    @Override
    public @NotNull CompletableFuture<Boolean> setCounter(@NotNull String name, long count, long capacity) {
        final JsonObject body = new JsonObject();
        body.addProperty("count", String.valueOf(count));
        body.addProperty("capacity", String.valueOf(capacity));
        return send("PATCH", "v1beta1/counters/" + name, body.toString());
    }

    private CompletableFuture<Boolean> send(String method, String path, String body) {
        if (base == null) {
            return CompletableFuture.completedFuture(false);
        }

        final HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> {
                    final boolean ok = response.statusCode() / 100 == 2;
                    record(ok, path, "status " + response.statusCode());
                    return ok;
                })
                .exceptionally(error -> {
                    record(false, path, error.getMessage());
                    return false;
                });
    }

    // Only the first failure in a run is logged, since health pings repeat every few seconds.
    private void record(boolean ok, String path, String detail) {
        if (ok) {
            if (failing.compareAndSet(true, false)) {
                log.info("Agones sidecar is answering again").submit();
            }
        } else if (failing.compareAndSet(false, true)) {
            log.warn("Agones sidecar call {} failed: {}", path, detail).submit();
        }
    }

}
