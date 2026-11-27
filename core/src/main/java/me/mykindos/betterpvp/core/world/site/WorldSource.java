package me.mykindos.betterpvp.core.world.site;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Where an instance's world comes from.
 */
@Value
public class WorldSource {

    public enum Kind {
        /** An existing world on disk, used directly and never copied or deleted. */
        ADOPT,
        /** A template folder copied per instance and deleted when the instance is reaped. */
        CLONE,
        /** A folder under which each owner's world is kept, made once and never deleted. */
        OWN,
        /**
         * One world per owner built in memory from a slime template each time it opens and never saved. Without a
         * slime loader installed, or without a slime file for the template, it is kept on disk exactly as
         * {@link #OWN} is.
         */
        SLIME
    }

    @NotNull Kind kind;

    /** A world name for {@link Kind#ADOPT}, otherwise a folder path. */
    @NotNull String value;

    /**
     * What an owner's world is made from, or null to generate one. Only {@link Kind#OWN} and {@link Kind#SLIME} use
     * it. An owned world on disk is made from it only the first time that owner is given a world, so editing it
     * afterwards changes nothing that already exists.
     */
    @Nullable String template;

    public static @NotNull WorldSource adopt(@NotNull String worldName) {
        return new WorldSource(Kind.ADOPT, worldName, null);
    }

    public static @NotNull WorldSource clone(@NotNull String templateFolder) {
        return new WorldSource(Kind.CLONE, templateFolder, null);
    }

    public static @NotNull WorldSource own(@NotNull String folder) {
        return new WorldSource(Kind.OWN, folder, null);
    }

    public static @NotNull WorldSource own(@NotNull String folder, @Nullable String template) {
        return new WorldSource(Kind.OWN, folder, template);
    }

    public static @NotNull WorldSource slime(@NotNull String folder, @Nullable String template) {
        return new WorldSource(Kind.SLIME, folder, template);
    }

    /** Whether each owner has a world of their own. */
    public boolean isOwned() {
        return kind == Kind.OWN || kind == Kind.SLIME;
    }
}
