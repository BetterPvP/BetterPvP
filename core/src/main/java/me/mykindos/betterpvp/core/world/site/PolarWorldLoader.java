package me.mykindos.betterpvp.core.world.site;

import com.google.inject.Singleton;
import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.source.BytesPolarSource;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Slime worlds through the Polar plugin. Everything that names a Polar type sits in {@link Calls}, which is only
 * loaded once Polar is known to be installed, so this class links on a server without it.
 */
@Singleton
public class PolarWorldLoader implements SlimeWorldLoader {

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("polarpaper");
    }

    @Override
    public @NotNull CompletableFuture<World> create(byte @NotNull [] template, @NotNull String worldName) {
        return Calls.create(template, worldName).thenApply(world -> {
            if (world == null) {
                throw new IllegalStateException("Polar could not create world " + worldName);
            }
            return world;
        });
    }

    @Override
    public boolean unload(@NotNull World world) {
        return Bukkit.unloadWorld(world, false);
    }

    private static final class Calls {

        private static CompletableFuture<World> create(byte[] template, String worldName) {
            // Polar's defaults turn off random ticks, which would stop crops growing on a camp's farm.
            final Config config = Config.Builder.defaults()
                    .loadOnStartup(false)
                    .autoSaveIntervalTicks(-1)
                    .saveOnStop(false)
                    .gamerules(Map.of("spawn_mobs", false, "blockPhysics", true, "blockGravity", true,
                            "liquidPhysics", true, "blockFade", true))
                    .build();
            // The key keeps Bukkit's name for the world, so Bukkit.getWorld(worldName) finds it.
            return Polar.createWorld(new BytesPolarSource(template), WorldCreator.name(worldName).key().toString(), config);
        }
    }
}
