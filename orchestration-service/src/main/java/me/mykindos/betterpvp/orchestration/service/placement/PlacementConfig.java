package me.mykindos.betterpvp.orchestration.service.placement;

import lombok.Value;
import me.mykindos.betterpvp.network.protocol.Subjects;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/** The {@code [placement]} section of the service config. */
@Value
public class PlacementConfig {

    boolean enabled;
    String natsUrl;
    String natsToken;
    String prefix;
    int replicas;
    Duration leaseTtl;

    /** The role a site runs on when {@link #sites} does not name one. */
    String defaultRole;
    Map<String, String> sites;
    Map<String, AgonesServerPool.Fleet> fleets;

    String kubernetesUrl;
    /** Empty means the namespace the service account lives in. */
    String namespace;
    String tokenFile;
    String caFile;

    public String roleOf(String siteId) {
        return sites.getOrDefault(siteId, defaultRole);
    }

    public static PlacementConfig from(TomlParseResult toml) {
        final Map<String, String> sites = new HashMap<>();
        final TomlTable siteTable = toml.getTable("placement.sites");
        if (siteTable != null) {
            siteTable.keySet().forEach(site -> sites.put(site, siteTable.getString(site)));
        }

        final Map<String, AgonesServerPool.Fleet> fleets = new HashMap<>();
        final TomlTable fleetTable = toml.getTable("placement.fleets");
        if (fleetTable != null) {
            for (String role : fleetTable.keySet()) {
                final TomlTable fleet = fleetTable.getTable(role);
                if (fleet != null) {
                    fleets.put(role, new AgonesServerPool.Fleet(fleet.getString("fleet", () -> role),
                            fleet.getString("counter", () -> role + "-slots")));
                }
            }
        }

        return new PlacementConfig(
                toml.getBoolean("placement.enabled", () -> false),
                toml.getString("placement.nats_url", () -> "nats://127.0.0.1:4222"),
                toml.getString("placement.nats_token", () -> ""),
                toml.getString("placement.prefix", () -> Subjects.DEFAULT_PREFIX),
                Math.toIntExact(toml.getLong("placement.replicas", () -> 1L)),
                Duration.ofSeconds(toml.getLong("placement.lease_ttl_seconds", () -> 60L)),
                toml.getString("placement.default_role", () -> "camp"),
                Map.copyOf(sites),
                Map.copyOf(fleets),
                toml.getString("placement.kubernetes_url", () -> "https://kubernetes.default.svc"),
                toml.getString("placement.namespace", () -> ""),
                toml.getString("placement.token_file", () -> "/var/run/secrets/kubernetes.io/serviceaccount/token"),
                toml.getString("placement.ca_file", () -> "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"));
    }
}
