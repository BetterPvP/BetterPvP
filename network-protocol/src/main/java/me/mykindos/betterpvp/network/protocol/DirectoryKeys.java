package me.mykindos.betterpvp.network.protocol;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Where the site directory keeps its records in NATS JetStream key-value buckets.
 * <p>
 * Every value is a flat JSON object of strings, written with {@link EnvelopeCodec#encodeFields}. Names and ids are
 * escaped with {@link #token} before they become part of a key.
 *
 * <table>
 *   <tr><th>Bucket</th><th>Key</th><th>Value fields</th><th>Expires</th></tr>
 *   <tr><td>{@code <prefix>-instances}</td><td>{@code <site>.<owner>.<instance uuid>}</td>
 *       <td>id, site, owner, server, world, ready, occupants</td><td>30s after the last heartbeat</td></tr>
 *   <tr><td>{@code <prefix>-servers}</td><td>{@code <server>}</td><td>server, players</td>
 *       <td>30s after the last heartbeat</td></tr>
 *   <tr><td>{@code <prefix>-reserved}</td><td>{@code <instance uuid>}</td><td>seats</td>
 *       <td>60s after the last change</td></tr>
 *   <tr><td>{@code <prefix>-arrivals}</td><td>{@code <player uuid>}</td>
 *       <td>instance, site, owner, server, world, landing</td><td>60s</td></tr>
 *   <tr><td>{@code <prefix>-hosts}</td><td>{@code <site>.<owner>}</td><td>server</td><td>never</td></tr>
 *   <tr><td>{@code <prefix>-leases}</td><td>{@code <site>.<owner>}</td><td>server, placed</td>
 *       <td>never, Placement decides when a lease has lapsed</td></tr>
 * </table>
 */
public final class DirectoryKeys {

    private DirectoryKeys() {
    }

    public static String instances(String prefix) {
        return prefix + "-instances";
    }

    public static String servers(String prefix) {
        return prefix + "-servers";
    }

    public static String reserved(String prefix) {
        return prefix + "-reserved";
    }

    public static String arrivals(String prefix) {
        return prefix + "-arrivals";
    }

    public static String hosts(String prefix) {
        return prefix + "-hosts";
    }

    /** Which server holds each owned site, written only by the Placement service. */
    public static String leases(String prefix) {
        return prefix + "-leases";
    }

    public static String instance(String siteId, long ownerId, UUID instance) {
        return site(siteId, ownerId) + "." + instance;
    }

    /** Matches every instance of one site and owner. */
    public static String instancesOf(String siteId, long ownerId) {
        return site(siteId, ownerId) + ".*";
    }

    /** Matches one instance whatever site it belongs to. */
    public static String instanceById(UUID instance) {
        return "*.*." + instance;
    }

    public static String site(String siteId, long ownerId) {
        return token(siteId) + "." + ownerId;
    }

    /**
     * Escapes a value into one key token. Letters, digits, {@code _} and {@code -} stay as they are, and every other
     * UTF-8 byte becomes {@code =} and two hex digits, so {@code camp:north} becomes {@code camp=3Anorth}.
     */
    public static String token(String value) {
        final StringBuilder escaped = new StringBuilder(value.length());
        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            final int b = raw & 0xFF;
            if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9') || b == '_' || b == '-') {
                escaped.append((char) b);
            } else {
                escaped.append('=').append(String.format("%02X", b));
            }
        }
        return escaped.toString();
    }
}
