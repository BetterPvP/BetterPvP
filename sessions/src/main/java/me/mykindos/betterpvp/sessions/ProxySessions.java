package me.mykindos.betterpvp.sessions;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * What a proxy calls so one account is only ever on one proxy. Call {@link #login} before letting a player in,
 * {@link #logout} when they leave the network, and {@link #heartbeat} every {@link #HEARTBEAT_INTERVAL}.
 */
public class ProxySessions {

    public static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(5);

    private final SessionCalls calls;

    /** @param calls calls whose origin is this proxy's name */
    public ProxySessions(@NotNull SessionCalls calls) {
        this.calls = calls;
    }

    /** Completes true when the account may join through this proxy, false while another live proxy has it. */
    public @NotNull CompletableFuture<Boolean> login(@NotNull UUID player, @NotNull Duration timeout) {
        return calls.ask(SessionProtocol.LOGIN, Map.of(SessionProtocol.PLAYER, player.toString(),
                        SessionProtocol.PROXY, calls.origin()), timeout)
                .thenApply(answer -> SessionProtocol.ACCEPTED.equals(answer.get(SessionProtocol.RESULT)));
    }

    /** Completes once the account is free to log in through another proxy. */
    public @NotNull CompletableFuture<Void> logout(@NotNull UUID player, @NotNull Duration timeout) {
        return calls.ask(SessionProtocol.LOGOUT, Map.of(SessionProtocol.PLAYER, player.toString(),
                        SessionProtocol.PROXY, calls.origin()), timeout)
                .thenAccept(answer -> {
                });
    }

    public void heartbeat() {
        calls.tell(SessionProtocol.PROXY_HEARTBEAT, Map.of(SessionProtocol.PROXY, calls.origin()));
    }
}
