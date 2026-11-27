package me.mykindos.betterpvp.core.playerstate;

import com.google.inject.AbstractModule;

public class PlayerStateModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(PlayerStateStore.class).to(DatabasePlayerStateStore.class);
    }
}
