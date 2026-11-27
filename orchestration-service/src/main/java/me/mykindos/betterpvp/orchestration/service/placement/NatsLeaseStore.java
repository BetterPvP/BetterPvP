package me.mykindos.betterpvp.orchestration.service.placement;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueManagement;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.StorageType;
import me.mykindos.betterpvp.network.protocol.DirectoryKeys;
import me.mykindos.betterpvp.network.protocol.EnvelopeCodec;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Leases in the {@code <prefix>-leases} JetStream bucket, laid out as {@link DirectoryKeys} describes.
 * <p>
 * Renewal is read from the servers' own advertisements in {@code <prefix>-instances}, so a host renews its lease by
 * doing what it already does on every heartbeat.
 */
public class NatsLeaseStore implements LeaseStore {

    private final EnvelopeCodec codec = new EnvelopeCodec();
    private final Connection connection;
    private final String instancesBucket;
    private final KeyValue leases;

    private NatsLeaseStore(Connection connection, String instancesBucket, KeyValue leases) {
        this.connection = connection;
        this.instancesBucket = instancesBucket;
        this.leases = leases;
    }

    /** Opens the lease bucket, creating it if this is the first Placement to run. */
    public static NatsLeaseStore open(Connection connection, String prefix, int replicas) throws IOException {
        final String name = DirectoryKeys.leases(prefix);
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
                            .build());
                } catch (JetStreamApiException raced) {
                    // Another replica made it first, or JetStream is off. Asking again tells the two apart.
                    management.getStatus(name);
                }
            }
            return new NatsLeaseStore(connection, DirectoryKeys.instances(prefix), connection.keyValue(name));
        } catch (JetStreamApiException exception) {
            throw new IOException("Could not open the lease bucket " + name, exception);
        }
    }

    @Override
    public Optional<Lease> read(String siteId, long ownerId) throws IOException {
        try {
            final KeyValueEntry entry = leases.get(DirectoryKeys.site(siteId, ownerId));
            if (entry == null || entry.getValue() == null) {
                return Optional.empty();
            }
            final Map<String, String> fields = codec.decodeFields(entry.getValue());
            return Optional.of(new Lease(fields.get("server"), Long.parseLong(fields.getOrDefault("placed", "0")),
                    entry.getRevision()));
        } catch (JetStreamApiException exception) {
            throw new IOException("Could not read the lease on " + siteId, exception);
        }
    }

    @Override
    public boolean claim(String siteId, long ownerId, String server, long placed, Lease replacing) throws IOException {
        final String key = DirectoryKeys.site(siteId, ownerId);
        final byte[] value = codec.encodeFields(Map.of("server", server, "placed", String.valueOf(placed)));
        try {
            if (replacing == null) {
                leases.create(key, value);
            } else {
                leases.update(key, value, replacing.getRevision());
            }
            return true;
        } catch (JetStreamApiException lostRace) {
            return false;
        }
    }

    @Override
    public boolean isAdvertised(String siteId, long ownerId, String server) throws IOException {
        try {
            if (!connection.keyValueManagement().getBucketNames().contains(instancesBucket)) {
                // No server has advertised anything yet.
                return false;
            }
            final KeyValue instances = connection.keyValue(instancesBucket);
            for (String key : instances.keys(DirectoryKeys.instancesOf(siteId, ownerId))) {
                final KeyValueEntry entry = instances.get(key);
                if (entry != null && entry.getValue() != null
                        && server.equals(codec.decodeFields(entry.getValue()).get("server"))) {
                    return true;
                }
            }
            return false;
        } catch (JetStreamApiException exception) {
            throw new IOException("Could not read advertisements for " + siteId, exception);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted reading advertisements", interrupted);
        }
    }
}
