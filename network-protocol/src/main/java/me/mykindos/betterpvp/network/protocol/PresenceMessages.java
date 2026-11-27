package me.mykindos.betterpvp.network.protocol;

/**
 * Who is online across the network, as the proxies announce it.
 * <ul>
 *     <li>{@link #ONLINE}: a player got in through a proxy. Carries {@link #PLAYER}, {@link #NAME} and {@link #PROXY}.</li>
 *     <li>{@link #OFFLINE}: a player left. Carries {@link #PLAYER} and {@link #PROXY}.</li>
 *     <li>{@link #ROSTER}: a proxy's whole list, every {@link #ROSTER_INTERVAL_SECONDS} seconds. Carries {@link #PROXY}
 *     and {@link #PLAYERS}, written by {@link PlayerRoster#encode}.</li>
 * </ul>
 * All three are plain publishes. {@link PlayerRoster} turns them into one list.
 */
public final class PresenceMessages {

    public static final String ONLINE = "presence.online";
    public static final String OFFLINE = "presence.offline";
    public static final String ROSTER = "presence.roster";

    public static final String PLAYER = "player";
    public static final String NAME = "name";
    public static final String PROXY = "proxy";
    public static final String PLAYERS = "players";

    public static final int ROSTER_INTERVAL_SECONDS = 5;

    private PresenceMessages() {
    }
}
