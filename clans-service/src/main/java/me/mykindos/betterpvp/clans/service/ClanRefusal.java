package me.mykindos.betterpvp.clans.service;

import org.jetbrains.annotations.NotNull;

/** A mutation the service will not apply, with the reason sent back to the server that asked. */
public class ClanRefusal extends Exception {

    public ClanRefusal(@NotNull String reason) {
        super(reason);
    }
}
