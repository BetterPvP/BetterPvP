package me.mykindos.betterpvp.core.playerstate.session;

import me.mykindos.betterpvp.sessions.SessionCalls;
import me.mykindos.betterpvp.sessions.SessionProtocol;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Leases held through the sessions service, which this server reaches over NATS. */
public class NatsPlayerLeases implements PlayerLeases {

    private final SessionCalls calls;
    private final Duration timeout;

    /**
     * @param calls   calls whose origin is this server's name
     * @param timeout how long to wait for the service on top of any claim wait
     */
    public NatsPlayerLeases(@NotNull SessionCalls calls, @NotNull Duration timeout) {
        this.calls = calls;
        this.timeout = timeout;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public @NotNull CompletableFuture<OptionalLong> claim(@NotNull UUID player, @NotNull Duration wait) {
        return calls.ask(SessionProtocol.CLAIM, Map.of(SessionProtocol.PLAYER, player.toString(),
                        SessionProtocol.SERVER, calls.origin(),
                        SessionProtocol.WAIT, String.valueOf(wait.toMillis())), wait.plus(timeout))
                .thenApply(answer -> SessionProtocol.GRANTED.equals(answer.get(SessionProtocol.RESULT))
                        ? OptionalLong.of(Long.parseLong(answer.get(SessionProtocol.EPOCH)))
                        : OptionalLong.empty());
    }

    @Override
    public @NotNull CompletableFuture<Boolean> release(@NotNull UUID player, long epoch) {
        return calls.ask(SessionProtocol.RELEASE, Map.of(SessionProtocol.PLAYER, player.toString(),
                        SessionProtocol.SERVER, calls.origin(),
                        SessionProtocol.EPOCH, String.valueOf(epoch)), timeout)
                .thenApply(answer -> SessionProtocol.RELEASED.equals(answer.get(SessionProtocol.RESULT)));
    }

    @Override
    public void heartbeat() {
        calls.tell(SessionProtocol.HEARTBEAT, Map.of(SessionProtocol.SERVER, calls.origin()));
    }
}
