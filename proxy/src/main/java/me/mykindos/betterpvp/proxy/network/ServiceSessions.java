package me.mykindos.betterpvp.proxy.network;

import me.mykindos.betterpvp.sessions.ProxySessions;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Sessions kept by the sessions service. A login it does not answer in time fails, so the player is turned away. */
public class ServiceSessions implements NetworkSessions {

    private final ProxySessions sessions;

    public ServiceSessions(ProxySessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public CompletableFuture<Boolean> login(UUID player) {
        return sessions.login(player, Duration.ofSeconds(3));
    }

    @Override
    public CompletableFuture<Void> logout(UUID player) {
        return sessions.logout(player, Duration.ofSeconds(3));
    }

    @Override
    public void heartbeat() {
        sessions.heartbeat();
    }
}
