package me.mykindos.betterpvp.orchestration.service.placement;

import lombok.Value;

/** The server holding an owned site, as last written, with the store revision it was read at. */
@Value
public class Lease {

    String server;

    /** When the lease was written, in epoch milliseconds. */
    long placed;

    long revision;
}
