package me.mykindos.betterpvp.core.framework.net;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueManagement;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.StorageType;
import lombok.CustomLog;
import me.mykindos.betterpvp.network.protocol.DirectoryKeys;
import me.mykindos.betterpvp.network.protocol.EnvelopeCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * The directory backed by NATS JetStream key-value buckets, laid out as {@link DirectoryKeys} describes.
 * <p>
 * Expiry is per bucket: a heartbeat rewrites every entry, so an entry a dead server stops renewing ages out on its own.
 * Taking seats is a compare-and-set on the instance's seat count. Two servers reading the same count both try to
 * write the next one against the revision they read, and only one write lands. The other reads again and finds the
 * seats gone.
 */
@CustomLog
public class NatsSiteDirectory implements SiteDirectory {

    /** More servers than this racing for the same seats at once is not a real load. */
    private static final int MAX_ATTEMPTS = 16;

    private final EnvelopeCodec codec = new EnvelopeCodec();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final Connection connection;
    private final Supplier<String> server;
    private final KeyValue instances;
    private final KeyValue servers;
    private final KeyValue reserved;
    private final KeyValue arrivals;
    private final KeyValue hosts;

    private NatsSiteDirectory(@NotNull Connection connection, @NotNull Supplier<String> server,
                              @NotNull KeyValue instances, @NotNull KeyValue servers, @NotNull KeyValue reserved,
                              @NotNull KeyValue arrivals, @NotNull KeyValue hosts) {
        this.connection = connection;
        this.server = server;
        this.instances = instances;
        this.servers = servers;
        this.reserved = reserved;
        this.arrivals = arrivals;
        this.hosts = hosts;
    }

    /**
     * Opens the buckets, creating any that do not exist yet.
     *
     * @param server the name of this server, read whenever it advertises
     * @return the directory, or {@code null} if JetStream cannot be used here
     */
    public static @Nullable NatsSiteDirectory open(@NotNull Connection connection, @NotNull String prefix,
                                                   int replicas, @NotNull Supplier<String> server) {
        try {
            final KeyValueManagement management = connection.keyValueManagement();
            // Heartbeat entries outlive a slow tick but let a dead server's instances go quickly. A player who never
            // arrives stops holding seats after a minute.
            return new NatsSiteDirectory(connection, server,
                    bucket(connection, management, DirectoryKeys.instances(prefix), Duration.ofSeconds(30), StorageType.Memory, replicas),
                    bucket(connection, management, DirectoryKeys.servers(prefix), Duration.ofSeconds(30), StorageType.Memory, replicas),
                    bucket(connection, management, DirectoryKeys.reserved(prefix), Duration.ofSeconds(60), StorageType.Memory, replicas),
                    bucket(connection, management, DirectoryKeys.arrivals(prefix), Duration.ofSeconds(60), StorageType.Memory, replicas),
                    // An owned site's host has to outlive a NATS restart, or its world would be looked for elsewhere.
                    bucket(connection, management, DirectoryKeys.hosts(prefix), null, StorageType.File, replicas));
        } catch (IOException | JetStreamApiException exception) {
            log.warn("Could not open the NATS site directory, is JetStream enabled?", exception).submit();
            return null;
        }
    }

    private static @NotNull KeyValue bucket(@NotNull Connection connection, @NotNull KeyValueManagement management,
                                            @NotNull String name, @Nullable Duration ttl, @NotNull StorageType storage,
                                            int replicas) throws IOException, JetStreamApiException {
        try {
            management.getStatus(name);
        } catch (JetStreamApiException missing) {
            final KeyValueConfiguration.Builder config = KeyValueConfiguration.builder()
                    .name(name)
                    .maxHistoryPerKey(1)
                    .storageType(storage)
                    .replicas(replicas);
            if (ttl != null) {
                config.ttl(ttl);
            }
            try {
                management.create(config.build());
            } catch (JetStreamApiException raced) {
                // Another server made it first, or JetStream is off. Asking again tells the two apart.
                management.getStatus(name);
            }
        }
        return connection.keyValue(name);
    }

    @Override
    public void publish(@NotNull Collection<RemoteInstance> advertised, @NotNull ServerRoom room) {
        final String self = server.get();
        final List<RemoteInstance> snapshot = List.copyOf(advertised);

        run(() -> {
            try {
                servers.put(DirectoryKeys.token(self), codec.encodeFields(Map.of(
                        "server", self,
                        "players", String.valueOf(snapshot.stream().mapToInt(RemoteInstance::getOccupants).sum()),
                        "online", String.valueOf(room.getOnline()),
                        "limit", String.valueOf(room.getLimit()))));

                for (RemoteInstance instance : snapshot) {
                    // The holding server is the only one that knows how many people are in an instance, so it writes
                    // that outright. Seats held for parties in transit live in their own bucket.
                    instances.put(DirectoryKeys.instance(instance.getSiteId(), instance.getOwnerId(), instance.getId()),
                            codec.encodeFields(Map.of(
                                    "id", instance.getId().toString(),
                                    "site", instance.getSiteId(),
                                    "owner", String.valueOf(instance.getOwnerId()),
                                    "server", instance.getServer(),
                                    "world", instance.getWorld(),
                                    "ready", String.valueOf(instance.isReady()),
                                    "occupants", String.valueOf(instance.getOccupants()))));
                }
            } catch (Exception exception) {
                log.error("Failed to publish {} instance(s) to the directory", snapshot.size(), exception).submit();
            }
        });
    }

    @Override
    public @NotNull CompletableFuture<List<RemoteInstance>> lookup(@NotNull String siteId, long ownerId) {
        return async(() -> {
            final List<RemoteInstance> found = new ArrayList<>();
            for (String key : instances.keys(DirectoryKeys.instancesOf(siteId, ownerId))) {
                final Map<String, String> fields = read(instances, key);
                if (fields == null) {
                    continue;
                }

                // Seats held for parties in transit count against capacity, or two servers would each fill the
                // last place in an instance and both believe they had it.
                final UUID id = UUID.fromString(fields.get("id"));
                found.add(new RemoteInstance(id, fields.get("site"), Long.parseLong(fields.getOrDefault("owner", "0")),
                        fields.get("server"), fields.get("world"), Boolean.parseBoolean(fields.get("ready")),
                        Integer.parseInt(fields.getOrDefault("occupants", "0")) + seatsHeld(id)));
            }
            return found;
        }, List.of());
    }

    @Override
    public @NotNull CompletableFuture<Boolean> reserve(@NotNull UUID instance, int seats, int capacity) {
        return async(() -> {
            final int occupants = occupants(instance);
            final String key = instance.toString();

            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                final KeyValueEntry entry = reserved.get(key);
                final int held = entry == null ? 0 : seats(entry);
                if (capacity > 0 && occupants + held + seats > capacity) {
                    return false;
                }

                final byte[] next = codec.encodeFields(Map.of("seats", String.valueOf(held + seats)));
                try {
                    if (entry == null) {
                        reserved.create(key, next);
                    } else {
                        reserved.update(key, next, entry.getRevision());
                    }
                    return true;
                } catch (JetStreamApiException lostRace) {
                    // Somebody else changed the count since it was read. Read it again and decide afresh.
                }
            }

            log.warn("Gave up taking {} seat(s) on {} after {} contested attempts", seats, instance, MAX_ATTEMPTS).submit();
            return false;
        }, false);
    }

    @Override
    public void release(@NotNull UUID instance, int seats) {
        run(() -> {
            final String key = instance.toString();
            try {
                for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                    final KeyValueEntry entry = reserved.get(key);
                    if (entry == null) {
                        return;
                    }

                    final int left = Math.max(0, seats(entry) - seats);
                    try {
                        reserved.update(key, codec.encodeFields(Map.of("seats", String.valueOf(left))),
                                entry.getRevision());
                        return;
                    } catch (JetStreamApiException lostRace) {
                        // Changed under us, so read it again.
                    }
                }
            } catch (Exception exception) {
                log.error("Failed to give back {} seat(s) on instance {}", seats, instance, exception).submit();
            }
        });
    }

    @Override
    public @NotNull CompletableFuture<String> stickyHost(@NotNull String siteId, long ownerId,
                                                         @NotNull String candidate) {
        return async(() -> {
            final String key = DirectoryKeys.site(siteId, ownerId);
            try {
                hosts.create(key, codec.encodeFields(Map.of("server", candidate)));
                return candidate;
            } catch (JetStreamApiException alreadyClaimed) {
                final Map<String, String> fields = read(hosts, key);
                return fields == null ? candidate : fields.getOrDefault("server", candidate);
            }
        }, candidate);
    }

    @Override
    public @NotNull CompletableFuture<Optional<ServerRoom>> room(@NotNull String server) {
        return async(() -> {
            final Map<String, String> fields = read(servers, DirectoryKeys.token(server));
            if (fields == null || !fields.containsKey("online") || !fields.containsKey("limit")) {
                return Optional.<ServerRoom>empty();
            }
            return Optional.of(new ServerRoom(server, Integer.parseInt(fields.get("online")),
                    Integer.parseInt(fields.get("limit"))));
        }, Optional.empty());
    }

    @Override
    public @NotNull CompletableFuture<List<String>> serversByLoad() {
        final String self = server.get();
        return async(() -> {
            final Map<String, Integer> load = new HashMap<>();
            for (String key : servers.keys()) {
                final Map<String, String> fields = read(servers, key);
                if (fields != null) {
                    load.put(fields.get("server"), Integer.parseInt(fields.getOrDefault("players", "0")));
                }
            }

            final List<String> alive = new ArrayList<>(load.keySet());
            alive.sort(Comparator.comparingInt(load::get));
            return alive;
        }, List.of(self));
    }

    @Override
    public void expect(@NotNull UUID player, @NotNull Arrival arrival) {
        final RemoteInstance instance = arrival.getInstance();
        run(() -> {
            final Map<String, String> fields = new HashMap<>(Map.of(
                    "instance", instance.getId().toString(),
                    "site", instance.getSiteId(),
                    "owner", String.valueOf(instance.getOwnerId()),
                    "server", instance.getServer(),
                    "world", instance.getWorld()));
            if (arrival.getLanding() != null) {
                fields.put("landing", arrival.getLanding());
            }

            try {
                arrivals.put(player.toString(), codec.encodeFields(fields));
            } catch (Exception exception) {
                log.error("Failed to record that {} is on their way to {}", player, instance.getId(), exception).submit();
            }
        });
    }

    @Override
    public @NotNull CompletableFuture<Optional<Arrival>> claimArrival(@NotNull UUID player) {
        return async(() -> {
            final String key = player.toString();
            final KeyValueEntry entry = arrivals.get(key);
            if (entry == null) {
                return Optional.empty();
            }

            try {
                // Deleting against the revision read means only one claimer gets it.
                arrivals.delete(key, entry.getRevision());
            } catch (JetStreamApiException claimedElsewhere) {
                return Optional.empty();
            }

            final Map<String, String> fields = codec.decodeFields(entry.getValue());
            return Optional.of(new Arrival(new RemoteInstance(UUID.fromString(fields.get("instance")),
                    fields.get("site"), Long.parseLong(fields.getOrDefault("owner", "0")), fields.get("server"),
                    fields.get("world"), true, 0), fields.get("landing")));
        }, Optional.empty());
    }

    @Override
    public boolean isAvailable() {
        return connection.getStatus() == Connection.Status.CONNECTED;
    }

    @Override
    public boolean isShared() {
        return true;
    }

    /** Stops taking work. The connection is shared with the bus and closed by the NatsConnection that owns it. */
    @Override
    public void close() {
        executor.shutdown();
    }

    private int occupants(@NotNull UUID instance) throws Exception {
        for (String key : instances.keys(DirectoryKeys.instanceById(instance))) {
            final Map<String, String> fields = read(instances, key);
            if (fields != null) {
                return Integer.parseInt(fields.getOrDefault("occupants", "0"));
            }
        }
        return 0;
    }

    private int seatsHeld(@NotNull UUID instance) throws Exception {
        final KeyValueEntry entry = reserved.get(instance.toString());
        return entry == null ? 0 : seats(entry);
    }

    private int seats(@NotNull KeyValueEntry entry) throws IOException {
        return Integer.parseInt(codec.decodeFields(entry.getValue()).getOrDefault("seats", "0"));
    }

    private @Nullable Map<String, String> read(@NotNull KeyValue bucket, @NotNull String key) throws Exception {
        final KeyValueEntry entry = bucket.get(key);
        return entry == null || entry.getValue() == null ? null : codec.decodeFields(entry.getValue());
    }

    /**
     * Runs a query off the calling thread, answering with {@code fallback} if NATS cannot be reached.
     * <p>
     * A directory that is down should degrade to this server acting alone, not to travel failing outright.
     */
    private <T> @NotNull CompletableFuture<T> async(@NotNull DirectoryQuery<T> query, @NotNull T fallback) {
        final CompletableFuture<T> answered = new CompletableFuture<>();
        final boolean accepted = run(() -> {
            try {
                answered.complete(query.run());
            } catch (Exception exception) {
                log.error("Directory query failed, carrying on as though this server were alone", exception).submit();
                answered.complete(fallback);
            }
        });
        if (!accepted) {
            answered.complete(fallback);
        }
        return answered;
    }

    /** Hands work to the directory's threads, or drops it once the directory is closed. */
    private boolean run(@NotNull Runnable task) {
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException closed) {
            return false;
        }
    }

    @FunctionalInterface
    private interface DirectoryQuery<T> {
        T run() throws Exception;
    }
}
