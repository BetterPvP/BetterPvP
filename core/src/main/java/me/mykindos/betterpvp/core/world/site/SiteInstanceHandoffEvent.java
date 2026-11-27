package me.mykindos.betterpvp.core.world.site;

import lombok.Getter;
import me.mykindos.betterpvp.core.framework.events.CustomEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Called when an instance's world has closed because another server is taking it over, on whichever thread closed it. Anything
 * kept about the instance must be written down before that server reads it, so a listener hands its write to
 * {@link #waitFor} and the hand-off goes on only once every write has finished.
 */
public class SiteInstanceHandoffEvent extends CustomEvent {

    @Getter
    private final @NotNull SiteInstance instance;
    private final List<CompletableFuture<?>> saves = new ArrayList<>();

    /** @param async whether it is called off the main thread */
    public SiteInstanceHandoffEvent(@NotNull SiteInstance instance, boolean async) {
        super(async);
        this.instance = instance;
    }

    public void waitFor(@NotNull CompletableFuture<?> save) {
        saves.add(save);
    }

    /** Completes once every write handed to {@link #waitFor} has, whether or not it succeeded. */
    public @NotNull CompletableFuture<Void> whenSaved() {
        return CompletableFuture.allOf(saves.stream()
                .map(save -> save.handle((unused, ex) -> null))
                .toArray(CompletableFuture[]::new));
    }
}
