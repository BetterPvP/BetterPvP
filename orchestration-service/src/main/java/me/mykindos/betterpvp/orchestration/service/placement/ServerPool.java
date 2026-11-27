package me.mykindos.betterpvp.orchestration.service.placement;

import lombok.Value;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** The game servers a role runs on, how much room each has, and how to get another. */
public interface ServerPool {

    /** Running servers of a role that are taking sites, with their free slots. */
    List<Slots> servers(String role) throws IOException;

    /** Brings one more server of a role into use, answering its name, or empty if none could be had. */
    Optional<String> allocate(String role) throws IOException;

    @Value
    class Slots {
        String server;
        int free;
    }
}
