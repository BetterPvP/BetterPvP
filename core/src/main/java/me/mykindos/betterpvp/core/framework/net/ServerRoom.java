package me.mykindos.betterpvp.core.framework.net;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/** How many players a server has online against the most it lets in, as it last reported to the directory. */
@Value
public class ServerRoom {

    @NotNull String server;
    int online;
    int limit;

    /** Whether {@code players} more can join, so a group that would not all fit is turned away together. */
    public boolean fits(int players) {
        return online + players <= limit;
    }
}
