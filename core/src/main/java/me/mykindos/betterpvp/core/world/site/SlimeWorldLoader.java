package me.mykindos.betterpvp.core.world.site;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Turns a slime template into a loaded world held only in memory, and unloads one without saving it.
 */
public interface SlimeWorldLoader {

    /** Whether a slime world can be made on this server at all. */
    boolean isAvailable();

    /** Makes a world named {@code worldName} from a template's bytes. Called on the main thread. */
    @NotNull CompletableFuture<World> create(byte @NotNull [] template, @NotNull String worldName);

    /** Unloads a world this made, saving nothing. Called on the main thread. */
    boolean unload(@NotNull World world);
}
