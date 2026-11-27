package me.mykindos.betterpvp.network.protocol;

import lombok.Value;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * One list of who is online across the network, kept from {@link PresenceMessages}. Thread-safe.
 * <p>
 * A proxy that stops sending its roster for longer than {@code staleAfter} is taken to be gone, and its players
 * with it, so a proxy that crashes leaves nobody behind.
 */
public class PlayerRoster {

    private final Map<UUID, Entry> players = new HashMap<>();
    private final Map<String, Long> proxiesSeen = new HashMap<>();
    private final Duration staleAfter;
    private final LongSupplier clock;

    /** @param clock epoch milliseconds */
    public PlayerRoster(Duration staleAfter, LongSupplier clock) {
        this.staleAfter = staleAfter;
        this.clock = clock;
    }

    /** Applies one presence message, ignoring anything that is not one. */
    public synchronized void apply(String topic, Map<String, String> payload) {
        final String proxy = payload.get(PresenceMessages.PROXY);
        if (proxy == null) {
            return;
        }

        switch (topic) {
            case PresenceMessages.ONLINE -> {
                proxiesSeen.put(proxy, clock.getAsLong());
                final UUID player = uuid(payload.get(PresenceMessages.PLAYER));
                if (player != null) {
                    players.put(player, new Entry(player, payload.getOrDefault(PresenceMessages.NAME, ""), proxy));
                }
            }
            case PresenceMessages.OFFLINE -> {
                final UUID player = uuid(payload.get(PresenceMessages.PLAYER));
                if (player != null && players.containsKey(player) && players.get(player).getProxy().equals(proxy)) {
                    players.remove(player);
                }
            }
            case PresenceMessages.ROSTER -> {
                proxiesSeen.put(proxy, clock.getAsLong());
                players.values().removeIf(entry -> entry.getProxy().equals(proxy));
                decode(payload.getOrDefault(PresenceMessages.PLAYERS, ""))
                        .forEach((player, name) -> players.put(player, new Entry(player, name, proxy)));
            }
            default -> {
            }
        }
    }

    /** Where a player is online, if a live proxy has them. */
    public synchronized Optional<Entry> find(UUID player) {
        final Entry entry = players.get(player);
        return entry != null && isLive(entry.getProxy()) ? Optional.of(entry) : Optional.empty();
    }

    /** Everybody online through a live proxy. */
    public synchronized List<Entry> online() {
        return players.values().stream().filter(entry -> isLive(entry.getProxy())).toList();
    }

    private boolean isLive(String proxy) {
        final Long seen = proxiesSeen.get(proxy);
        return seen != null && clock.getAsLong() - seen <= staleAfter.toMillis();
    }

    /** Writes a proxy's players as {@code uuid=name} pairs joined by commas. */
    public static String encode(Map<UUID, String> players) {
        return players.entrySet().stream()
                .map(player -> player.getKey() + "=" + player.getValue())
                .collect(Collectors.joining(","));
    }

    public static Map<UUID, String> decode(String encoded) {
        final Map<UUID, String> players = new HashMap<>();
        if (encoded.isBlank()) {
            return players;
        }
        for (String pair : encoded.split(",")) {
            final int split = pair.indexOf('=');
            final UUID player = uuid(split < 0 ? pair : pair.substring(0, split));
            if (player != null) {
                players.put(player, split < 0 ? "" : pair.substring(split + 1));
            }
        }
        return players;
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    @Value
    public static class Entry {
        UUID player;
        String name;
        String proxy;
    }
}
