package me.mykindos.betterpvp.core.world.site.storage;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.CustomLog;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.world.site.SiteKey;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/**
 * Moves site records kept as files under {@code plugins/Core/sites/<site>/<owner>.json} into the bound
 * {@link SiteStorage}, once.
 * <p>
 * A record the store already has is left alone. Once every file is in, the folder is renamed to {@code sites.imported}
 * so the next start finds nothing to do. A folder with a file that failed stays where it is and is tried again.
 */
@Singleton
@CustomLog
public class LocalSiteRecordImport {

    private final SiteStorage storage;
    private final Path root;

    @Inject
    public LocalSiteRecordImport(@NotNull Core core, @NotNull SiteStorage storage) {
        this(new File(core.getDataFolder(), "sites").toPath(), storage);
    }

    LocalSiteRecordImport(@NotNull Path root, @NotNull SiteStorage storage) {
        this.storage = storage;
        this.root = root;
    }

    /** Imports every file found and answers how many records the store took. Blocks until it is done. */
    public int run() {
        if (!Files.isDirectory(root)) {
            return 0;
        }

        int imported = 0;
        boolean failed = false;
        for (Path file : files()) {
            final SiteKey key = keyOf(file);
            if (key == null) {
                continue;
            }

            try {
                storage.write(key, Files.readAllBytes(file), 0).join();
                imported++;
            } catch (CompletionException ex) {
                if (!(ex.getCause() instanceof StaleSiteRecordException)) {
                    failed = true;
                    log.error("Could not import the site record {}", file, ex).submit();
                }
            } catch (IOException ex) {
                failed = true;
                log.error("Could not read the site record {}", file, ex).submit();
            }
        }

        if (!failed) {
            try {
                Files.move(root, root.resolveSibling(root.getFileName() + ".imported"));
            } catch (IOException ex) {
                log.error("Imported site records but could not move {} aside", root, ex).submit();
            }
        }

        log.info("Imported {} site records from {}", imported, root).submit();
        return imported;
    }

    private @NotNull List<Path> files() {
        try (Stream<Path> walk = Files.walk(root, 2)) {
            return walk.filter(Files::isRegularFile).toList();
        } catch (IOException ex) {
            log.error("Could not list the site records in {}", root, ex).submit();
            return List.of();
        }
    }

    /** The site and owner a file is named after, or null for a file that is not a record. */
    private SiteKey keyOf(@NotNull Path file) {
        final Path relative = root.relativize(file);
        final String name = file.getFileName().toString();
        if (relative.getNameCount() != 2 || !name.endsWith(".json")) {
            return null;
        }

        try {
            final long owner = Long.parseLong(name.substring(0, name.length() - ".json".length()));
            return SiteKey.of(relative.getName(0).toString(), owner);
        } catch (NumberFormatException notAnOwner) {
            return null;
        }
    }
}
