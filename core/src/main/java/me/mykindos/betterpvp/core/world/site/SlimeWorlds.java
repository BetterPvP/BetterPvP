package me.mykindos.betterpvp.core.world.site;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.core.utilities.UtilWorld;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Worlds built in memory from a slime template and thrown away when they close.
 * <p>
 * A template is {@code <template>.polar} under the world container. Its bytes are read once and kept, so every world
 * after the first is built without touching the disk. Files beside Minecraft's own that content reads from the world
 * folder, such as Mapper data, come from the {@code <template>} folder and are copied into the world's folder before
 * it loads. Closing a world unloads it without saving and deletes that folder.
 */
@CustomLog
@Singleton
public class SlimeWorlds {

    private final Core core;
    private final SlimeWorldLoader loader;
    private final Path container;
    private final Function<String, Path> worldFolder;
    private final Map<String, byte[]> templates = new ConcurrentHashMap<>();
    private final Map<String, World> open = new ConcurrentHashMap<>();

    @Inject
    public SlimeWorlds(@NotNull Core core, @NotNull SlimeWorldLoader loader) {
        this(core, loader, Bukkit.getWorldContainer().toPath(), UtilWorld::getStorageFolder);
    }

    SlimeWorlds(@NotNull Core core, @NotNull SlimeWorldLoader loader, @NotNull Path container,
                @NotNull Function<String, Path> worldFolder) {
        this.core = core;
        this.loader = loader;
        this.container = container;
        this.worldFolder = worldFolder;
    }

    /** Whether a world can be built from this template here: a slime loader is installed and the file exists. */
    public boolean canBuild(@NotNull String template) {
        return loader.isAvailable() && (templates.containsKey(template) || Files.isRegularFile(slimeFile(template)));
    }

    /** Whether this world was built here as a slime world and is still open. */
    public boolean isOpen(@NotNull String worldName) {
        return open.containsKey(worldName);
    }

    /** Builds a world from a template. Whatever was in the world's folder before is discarded first. */
    public @NotNull CompletableFuture<World> open(@NotNull String template, @NotNull String worldName) {
        final CompletableFuture<byte[]> bytes = new CompletableFuture<>();
        UtilServer.runTaskAsync(core, () -> {
            try {
                final byte[] read = templates.computeIfAbsent(template, this::readTemplate);
                prepareFolder(template, worldName);
                bytes.complete(read);
            } catch (Exception ex) {
                bytes.completeExceptionally(ex);
            }
        });

        return bytes.thenCompose(read -> {
            final CompletableFuture<World> created = new CompletableFuture<>();
            UtilServer.runTask(core, () -> loader.create(read, worldName).whenComplete((world, ex) -> {
                if (ex != null) {
                    created.completeExceptionally(ex);
                    return;
                }
                open.put(worldName, world);
                log.info("Built slime world '{}' from '{}'", worldName, template).submit();
                created.complete(world);
            }));
            return created;
        });
    }

    /** Unloads a world without saving and deletes its folder. Must be called on the main thread. */
    public @NotNull CompletableFuture<Void> close(@NotNull String worldName) {
        final World world = open.get(worldName);
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (!loader.unload(world)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Could not unload world: " + worldName));
        }
        open.remove(worldName);

        final CompletableFuture<Void> future = new CompletableFuture<>();
        final File folder = worldFolder.apply(worldName).toFile();
        UtilServer.runTaskAsync(core, () -> {
            try {
                if (folder.exists()) {
                    FileUtils.forceDelete(folder);
                }
                future.complete(null);
            } catch (IOException ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    private @NotNull Path slimeFile(@NotNull String template) {
        return container.resolve(template + ".polar");
    }

    private byte @NotNull [] readTemplate(@NotNull String template) {
        try {
            return Files.readAllBytes(slimeFile(template));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read slime template " + slimeFile(template), ex);
        }
    }

    private void prepareFolder(@NotNull String template, @NotNull String worldName) throws IOException {
        final File target = worldFolder.apply(worldName).toFile();
        if (target.exists()) {
            FileUtils.forceDelete(target);
        }

        // What the slime file replaces is never copied from the template folder.
        final Set<String> worldData = Set.of("region", "entities", "poi", "DIM-1", "DIM1", "level.dat",
                "level.dat_old", "session.lock", "uid.dat", "playerdata", "stats", "advancements");
        final File[] extras = container.resolve(template).toFile().listFiles(file -> !worldData.contains(file.getName()));
        if (extras == null) {
            return;
        }

        for (File extra : extras) {
            final File copy = new File(target, extra.getName());
            if (extra.isDirectory()) {
                FileUtils.copyDirectory(extra, copy);
            } else {
                FileUtils.copyFile(extra, copy);
            }
        }
    }
}
