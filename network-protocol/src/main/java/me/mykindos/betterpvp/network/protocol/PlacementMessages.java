package me.mykindos.betterpvp.network.protocol;

/**
 * Asking the Placement service which server hosts a site.
 * <p>
 * A request on topic {@link #PLACE} carries {@link #SITE}, {@link #OWNER} (0 for none), {@link #OWNED} and
 * {@link #ASKER}. Placement answers with {@link #SERVER} and {@link #OUTCOME}, or with outcome {@link #UNAVAILABLE}
 * and a {@link #REASON}. Only Placement answers this topic.
 * <p>
 * An owned site is held by a lease in {@link DirectoryKeys#leases}, keyed by {@link DirectoryKeys#site}, so the same
 * site never opens on two servers. A site that is not owned gets a server for one new instance and no lease.
 */
public final class PlacementMessages {

    public static final String PLACE = "placement.place";

    public static final String SITE = "site";
    public static final String OWNER = "owner";
    public static final String OWNED = "owned";
    public static final String ASKER = "asker";

    public static final String SERVER = "server";
    public static final String OUTCOME = "outcome";
    public static final String REASON = "reason";

    /** A live lease already names the host. */
    public static final String HOSTED = "hosted";
    /** A running server with free slots was chosen. */
    public static final String PLACED = "placed";
    /** No server had room, so a new one was allocated. */
    public static final String ALLOCATED = "allocated";
    /** Nowhere could take it. */
    public static final String UNAVAILABLE = "unavailable";

    private PlacementMessages() {
    }
}
