package me.mykindos.betterpvp.game.command.loader;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommandLoader;
import me.mykindos.betterpvp.game.GamePlugin;

@Singleton
public class BrigadierGameCommandLoader extends BrigadierCommandLoader {
    @Inject
    public BrigadierGameCommandLoader(GamePlugin plugin) {
        super(plugin);
    }
}
