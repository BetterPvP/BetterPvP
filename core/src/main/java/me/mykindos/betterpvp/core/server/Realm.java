package me.mykindos.betterpvp.core.server;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One row of the realms table, the partition clans, stats and achievements are stored under. Servers that share
 * a realm name and season share a realm. Two realms are equal when their ids are, so the running server's realm,
 * which carries this server, matches the same realm loaded from the database.
 */
@Data
public class Realm implements Period {
    private final int id;
    @EqualsAndHashCode.Exclude
    private final Server server;
    @EqualsAndHashCode.Exclude
    private final Season season;

}
