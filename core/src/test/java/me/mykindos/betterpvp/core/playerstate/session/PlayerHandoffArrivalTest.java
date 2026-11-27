package me.mykindos.betterpvp.core.playerstate.session;

import me.mykindos.betterpvp.core.playerstate.PlayerStateCapture;
import me.mykindos.betterpvp.core.playerstate.PlayerStateStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("A player whose state cannot be loaded on arrival")
class PlayerHandoffArrivalTest {

    @Test
    @DisplayName("is released again, so another server can claim them")
    void failedLoadReleasesTheLease() {
        final UUID player = UUID.randomUUID();
        final PlayerLeases leases = mock(PlayerLeases.class);
        final PlayerStateStore store = mock(PlayerStateStore.class);
        when(leases.claim(any(), any())).thenReturn(CompletableFuture.completedFuture(OptionalLong.of(3)));
        when(leases.release(any(), anyLong())).thenReturn(CompletableFuture.completedFuture(true));
        when(store.fence(any(), anyLong())).thenReturn(CompletableFuture.completedFuture(null));
        when(store.load(player)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database down")));

        final PlayerHandoff handoff = new PlayerHandoff(leases, store, mock(PlayerStateCapture.class));

        assertThrows(CompletionException.class, () -> handoff.arrive(player, Duration.ofSeconds(1)).join());
        verify(leases).release(player, 3);
        assertEquals(Set.of(), handoff.owned());
    }
}
