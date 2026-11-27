package me.mykindos.betterpvp.core.world.site;

import me.mykindos.betterpvp.core.Core;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Building camp worlds in memory from slime templates")
class SlimeWorldsTest {

    private static final String TEMPLATE = "templates/camps/shore";

    @TempDir
    private Path container;

    private Loader loader;
    private SlimeWorlds slime;

    @BeforeEach
    void setUp() throws Exception {
        // A template the size of a real camp, with Mapper data beside it and world data that must not be copied.
        final byte[] polar = new byte[2 * 1024 * 1024];
        new Random(7).nextBytes(polar);
        Files.createDirectories(container.resolve("templates/camps"));
        Files.write(container.resolve(TEMPLATE + ".polar"), polar);
        Files.createDirectories(container.resolve(TEMPLATE).resolve("region"));
        Files.writeString(container.resolve(TEMPLATE).resolve("regions.json"), "[]");
        Files.writeString(container.resolve(TEMPLATE).resolve("level.dat"), "old");

        final Core core = mock(Core.class);
        // A disabled plugin runs its tasks inline, which is what makes the futures here already complete.
        when(core.isEnabled()).thenReturn(false);
        loader = new Loader();
        slime = new SlimeWorlds(core, loader, container, name -> container.resolve("dimensions").resolve(name));
    }

    private Path folderOf(String worldName) {
        return container.resolve("dimensions").resolve(worldName);
    }

    @Test
    @DisplayName("a camp opens in under a second on a warm server")
    void aWarmOpenIsFast() throws Exception {
        slime.open(TEMPLATE, "camps/1").join();
        slime.close("camps/1").join();
        Files.delete(container.resolve(TEMPLATE + ".polar"));

        final long started = System.nanoTime();
        final World world = slime.open(TEMPLATE, "camps/2").join();
        final long millis = (System.nanoTime() - started) / 1_000_000;

        assertTrue(millis < 1000, "opening took " + millis + "ms");
        assertEquals("camps/2", world.getName());
        assertEquals(2, loader.created, "the template is built from memory once it has been read");
        assertTrue(Files.isRegularFile(folderOf("camps/2").resolve("regions.json")), "Mapper data comes along");
        assertFalse(Files.exists(folderOf("camps/2").resolve("level.dat")), "world data comes from the slime file");
        assertFalse(Files.exists(folderOf("camps/2").resolve("region")));
    }

    @Test
    @DisplayName("50 camps can be loaded and unloaded in a loop with no memory growth")
    void fiftyCampsLeaveNothingBehind() {
        final List<WeakReference<World>> built = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            final String name = "camps/" + i;
            built.add(new WeakReference<>(slime.open(TEMPLATE, name).join()));
            slime.close(name).join();

            assertFalse(slime.isOpen(name));
            assertFalse(Files.exists(folderOf(name)), "a closed camp leaves no folder");
        }

        assertEquals(50, loader.unloaded);
        for (int attempt = 0; attempt < 20 && built.stream().anyMatch(ref -> ref.get() != null); attempt++) {
            System.gc();
        }
        assertTrue(built.stream().allMatch(ref -> ref.get() == null), "nothing holds on to a closed world");
    }

    @Test
    @DisplayName("without a slime loader, or a slime file, the world is left to the folder copy")
    void fallsBackWithoutPolar() {
        assertTrue(slime.canBuild(TEMPLATE));
        assertFalse(slime.canBuild("templates/camps/missing"));

        loader.available = false;
        assertFalse(slime.canBuild(TEMPLATE));
    }

    @Test
    @DisplayName("whatever was on disk for a world is discarded before it is built")
    void staleFoldersAreDiscarded() throws Exception {
        Files.createDirectories(folderOf("camps/3").resolve("region"));
        Files.writeString(folderOf("camps/3").resolve("region").resolve("r.0.0.mca"), "old blocks");

        slime.open(TEMPLATE, "camps/3").join();

        assertFalse(Files.exists(folderOf("camps/3").resolve("region")));
        assertTrue(slime.isOpen("camps/3"));
    }

    /** Stands in for Polar: hands back a bare world and counts what it was asked to do. */
    private static final class Loader implements SlimeWorldLoader {

        private boolean available = true;
        private int created;
        private int unloaded;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public @NotNull CompletableFuture<World> create(byte @NotNull [] template, @NotNull String worldName) {
            created++;
            return CompletableFuture.completedFuture(world(worldName));
        }

        @Override
        public boolean unload(@NotNull World world) {
            unloaded++;
            return true;
        }

        private static World world(String name) {
            return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getName" -> name;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "World(" + name + ")";
                        default -> null;
                    });
        }
    }
}
