package me.mykindos.betterpvp.sessions.service;

import lombok.Value;
import org.jetbrains.annotations.Nullable;

/** The answer to a claim: granted under an epoch, or held by another server. */
@Value
public class Claim {

    boolean granted;
    long epoch;
    @Nullable String holder;

    public static Claim granted(long epoch) {
        return new Claim(true, epoch, null);
    }

    public static Claim held(String holder) {
        return new Claim(false, 0, holder);
    }
}
