package me.mykindos.betterpvp.clans.clans.core.storage;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueManagement;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.StorageType;
import lombok.CustomLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Locks in the {@code <prefix>-clan-locks} JetStream bucket, one key per lock holding the holder's name.
 * <p>
 * Taking a lock creates its key, which fails while the key exists, so only one server holds it. Entries expire after
 * the lease, and the holding server rewrites its keys three times a lease, so a lock held by a server that died frees
 * itself.
 */
@CustomLog
public class NatsClanLocks implements ClanLocks, AutoCloseable {

    private final KeyValue bucket;
    private final Duration lease;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            runnable -> Thread.ofPlatform().name("clan-locks").daemon().unstarted(runnable));
    private final Map<String, Held> held = new ConcurrentHashMap<>();

    private NatsClanLocks(@NotNull KeyValue bucket, @NotNull Duration lease) {
        this.bucket = bucket;
        this.lease = lease;
    }

    /**
     * Opens the bucket, creating it if this is the first server to use it.
     *
     * @return the locks, or {@code null} if JetStream cannot be used here
     */
    public static @Nullable NatsClanLocks open(@NotNull Connection connection, @NotNull String prefix, int replicas,
                                               @NotNull Duration lease) {
        final String name = prefix + "-clan-locks";
        try {
            final KeyValueManagement management = connection.keyValueManagement();
            try {
                management.getStatus(name);
            } catch (JetStreamApiException missing) {
                try {
                    management.create(KeyValueConfiguration.builder()
                            .name(name)
                            .maxHistoryPerKey(1)
                            .storageType(StorageType.File)
                            .replicas(replicas)
                            .ttl(lease)
                            .build());
                } catch (JetStreamApiException raced) {
                    // Another server made it first, or JetStream is off. Asking again tells the two apart.
                    management.getStatus(name);
                }
            }
            return new NatsClanLocks(connection.keyValue(name), lease);
        } catch (IOException | JetStreamApiException exception) {
            log.warn("Could not open the clan lock bucket {}, is JetStream enabled?", name, exception).submit();
            return null;
        }
    }

    @Override
    public @NotNull CompletableFuture<LockAnswer> take(@NotNull String key, @NotNull String holder) {
        return CompletableFuture.supplyAsync(() -> attempt(key, holder), executor);
    }

    private @NotNull LockAnswer attempt(@NotNull String key, @NotNull String holder) {
        final byte[] value = holder.getBytes(StandardCharsets.UTF_8);
        try {
            final Held taken = new Held(value, bucket.create(key, value));
            final long every = Math.max(1, lease.toMillis() / 3);
            taken.renewal = executor.scheduleAtFixedRate(() -> renew(key, taken), every, every, TimeUnit.MILLISECONDS);
            held.put(key, taken);
            return LockAnswer.taken();
        } catch (JetStreamApiException exists) {
            return holderOf(key);
        } catch (IOException unreachable) {
            log.warn("Could not take the clan lock {}", key, unreachable).submit();
            return LockAnswer.unreachable();
        }
    }

    private @NotNull LockAnswer holderOf(@NotNull String key) {
        try {
            final KeyValueEntry entry = bucket.get(key);
            // An entry that is gone was freed between the two calls, and the next ask can take it.
            return entry == null || entry.getValue() == null
                    ? LockAnswer.unreachable()
                    : LockAnswer.held(new String(entry.getValue(), StandardCharsets.UTF_8));
        } catch (IOException | JetStreamApiException unreadable) {
            return LockAnswer.unreachable();
        }
    }

    private void renew(@NotNull String key, @NotNull Held taken) {
        if (held.get(key) != taken) {
            return;
        }
        try {
            taken.revision = bucket.update(key, taken.value, taken.revision);
        } catch (JetStreamApiException lost) {
            log.error("Lost the clan lock {}, another server may take it", key).submit();
            held.remove(key, taken);
            taken.renewal.cancel(false);
        } catch (IOException unreachable) {
            log.warn("Could not renew the clan lock {}, trying again", key, unreachable).submit();
        }
    }

    @Override
    public void release(@NotNull String key) {
        final Held taken = held.remove(key);
        if (taken == null) {
            return;
        }
        taken.renewal.cancel(false);
        executor.execute(() -> {
            try {
                bucket.delete(key, taken.revision);
            } catch (IOException | JetStreamApiException ex) {
                log.warn("Could not release the clan lock {}, it frees itself in {}", key, lease, ex).submit();
            }
        });
    }

    @Override
    public void close() {
        List.copyOf(held.keySet()).forEach(this::release);
        executor.shutdown();
    }

    private static final class Held {
        private final byte[] value;
        private volatile long revision;
        private ScheduledFuture<?> renewal;

        private Held(byte @NotNull [] value, long revision) {
            this.value = value;
            this.revision = revision;
        }
    }
}
