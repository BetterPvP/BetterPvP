package me.mykindos.betterpvp.proxy.network;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Binds an account to this proxy for as long as it is connected, so it is never on two proxies at once.
 * <p>
 * A login that completes exceptionally means nobody could be asked, and the player is turned away.
 */
public interface NetworkSessions {

    /** Completes true when the account may join through this proxy, false while another proxy has it. */
    CompletableFuture<Boolean> login(UUID player);

    /** Frees the account for other proxies. */
    CompletableFuture<Void> logout(UUID player);

    /** Says this proxy is still up, so the accounts bound to it stay bound. */
    void heartbeat();
}
