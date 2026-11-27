package me.mykindos.betterpvp.proxy.network;

import me.mykindos.betterpvp.network.protocol.PlayerRoster;
import me.mykindos.betterpvp.network.protocol.PresenceMessages;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Announces who is online through this proxy, and keeps the network's list from every proxy's announcements.
 */
public class PlayerPresence {

    private final ProxyBus bus;
    private final PlayerRoster roster;
    private final Map<UUID, String> here = new ConcurrentHashMap<>();

    public PlayerPresence(ProxyBus bus, PlayerRoster roster) {
        this.bus = bus;
        this.roster = roster;
    }

    /** Starts listening to the other proxies. */
    public void start() {
        bus.subscribe(PresenceMessages.ONLINE, roster::apply);
        bus.subscribe(PresenceMessages.OFFLINE, roster::apply);
        bus.subscribe(PresenceMessages.ROSTER, roster::apply);
    }

    public void online(UUID player, String name) {
        here.put(player, name);
        bus.publish(PresenceMessages.ONLINE, Map.of(PresenceMessages.PLAYER, player.toString(),
                PresenceMessages.NAME, name, PresenceMessages.PROXY, bus.origin()));
    }

    public void offline(UUID player) {
        if (here.remove(player) != null) {
            bus.publish(PresenceMessages.OFFLINE, Map.of(PresenceMessages.PLAYER, player.toString(),
                    PresenceMessages.PROXY, bus.origin()));
        }
    }

    /** Repeats this proxy's whole list, which is what keeps it counted as live. */
    public void publishRoster() {
        bus.publish(PresenceMessages.ROSTER, Map.of(PresenceMessages.PROXY, bus.origin(),
                PresenceMessages.PLAYERS, PlayerRoster.encode(Map.copyOf(here))));
    }

    /** The proxy a player is online through, when that is a live proxy other than this one. */
    public Optional<String> elsewhere(UUID player) {
        return roster.find(player)
                .map(PlayerRoster.Entry::getProxy)
                .filter(proxy -> !proxy.equals(bus.origin()));
    }

    public Map<UUID, String> here() {
        return Map.copyOf(here);
    }
}
