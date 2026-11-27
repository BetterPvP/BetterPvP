package me.mykindos.betterpvp.core.playerstate;

import lombok.Value;

/** A location kept by world name, since the world may not be loaded on the server that reads it. */
@Value
public class StoredPosition {

    String world;
    double x;
    double y;
    double z;
    float yaw;
    float pitch;
}
