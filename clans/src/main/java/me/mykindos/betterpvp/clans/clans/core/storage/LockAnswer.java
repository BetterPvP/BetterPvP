package me.mykindos.betterpvp.clans.clans.core.storage;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** How asking for a lock ended. */
@Value
public class LockAnswer {

    public enum Status {
        /** This server holds it until it lets go. */
        TAKEN,
        /** Someone else holds it. */
        HELD,
        /** Who holds it could not be found out, so it is treated as held. */
        UNREACHABLE
    }

    @NotNull Status status;
    /** Who holds it, for {@link Status#HELD}. */
    @Nullable String holder;

    public static @NotNull LockAnswer taken() {
        return new LockAnswer(Status.TAKEN, null);
    }

    public static @NotNull LockAnswer held(@NotNull String holder) {
        return new LockAnswer(Status.HELD, holder);
    }

    public static @NotNull LockAnswer unreachable() {
        return new LockAnswer(Status.UNREACHABLE, null);
    }

    public boolean isTaken() {
        return status == Status.TAKEN;
    }
}
