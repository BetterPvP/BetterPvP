package me.mykindos.betterpvp.core.playerstate;

import lombok.Value;

/**
 * An effect as saved with a player. {@code length} is the remaining time in milliseconds, or the effect's own
 * negative length when it never runs out.
 */
@Value
public class StoredEffect {

    String type;
    String name;
    int amplifier;
    long length;
    boolean permanent;
    boolean showParticles;
}
