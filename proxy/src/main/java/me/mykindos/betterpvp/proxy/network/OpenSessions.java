package me.mykindos.betterpvp.proxy.network;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Sessions for a network with no sessions service. Every login is let through, and the network player list is what
 * keeps an account off a second proxy.
 */
public class OpenSessions implements NetworkSessions {

    @Override
    public CompletableFuture<Boolean> login(UUID player) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Void> logout(UUID player) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void heartbeat() {
    }
}
