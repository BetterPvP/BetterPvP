package me.mykindos.betterpvp.proxy.network;

import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Who may come in through this proxy, and what the network is told when they come and go.
 * <p>
 * An account another live proxy has is turned away, first by the network player list and then by Sessions, which
 * also settles two proxies letting the same account in at the same moment. When Sessions cannot be asked nobody is
 * let in, since nothing could then stop the account being online twice.
 */
public class NetworkLogins {

    public enum Verdict {
        ACCEPTED,
        ONLINE_ELSEWHERE,
        UNAVAILABLE
    }

    private final NetworkSessions sessions;
    private final PlayerPresence presence;
    private final Logger logger;

    public NetworkLogins(NetworkSessions sessions, PlayerPresence presence, Logger logger) {
        this.sessions = sessions;
        this.presence = presence;
        this.logger = logger;
    }

    public CompletableFuture<Verdict> login(UUID player, String name) {
        if (presence.elsewhere(player).isPresent()) {
            return CompletableFuture.completedFuture(Verdict.ONLINE_ELSEWHERE);
        }

        return sessions.login(player)
                .thenApply(accepted -> {
                    if (!accepted) {
                        return Verdict.ONLINE_ELSEWHERE;
                    }
                    presence.online(player, name);
                    return Verdict.ACCEPTED;
                })
                .exceptionally(error -> {
                    logger.warn("Sessions could not be asked about {}, turning them away", name, error);
                    return Verdict.UNAVAILABLE;
                });
    }

    /** Lets an account go, if it came in through this proxy. */
    public CompletableFuture<Void> logout(UUID player) {
        if (!presence.here().containsKey(player)) {
            return CompletableFuture.completedFuture(null);
        }
        presence.offline(player);
        return sessions.logout(player).exceptionally(error -> {
            logger.warn("Sessions could not be told {} left", player, error);
            return null;
        });
    }

    /** Lets every account on this proxy go before it stops, so they can reconnect through another straight away. */
    public void shutdown() {
        final CompletableFuture<?>[] released = presence.here().keySet().stream()
                .map(this::logout)
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(released).get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            logger.warn("Not every player was released before the proxy stopped", exception);
        }
    }

    /** What a proxy repeats on a timer: that it is alive, and who it has. */
    public void heartbeat() {
        sessions.heartbeat();
        presence.publishRoster();
    }
}
