package me.mykindos.betterpvp.orchestration.service.placement;

import java.io.IOException;
import java.util.Optional;

/** Where leases on owned sites are kept, and how a host is seen renewing one. */
public interface LeaseStore {

    Optional<Lease> read(String siteId, long ownerId) throws IOException;

    /**
     * Writes a lease naming {@code server}, only if the stored lease is still {@code replacing}.
     *
     * @param replacing the lease read before deciding, or {@code null} if there was none
     * @return whether this write landed. False means somebody else wrote first.
     */
    boolean claim(String siteId, long ownerId, String server, long placed, Lease replacing) throws IOException;

    /**
     * Drops the lease, only if the stored lease is still {@code current}.
     *
     * @return whether it was dropped. False means somebody else wrote first.
     */
    boolean release(String siteId, long ownerId, Lease current) throws IOException;

    /** Whether {@code server} is currently advertising an instance of the site, which is how a host renews. */
    boolean isAdvertised(String siteId, long ownerId, String server) throws IOException;
}
