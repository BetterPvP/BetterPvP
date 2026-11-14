package me.mykindos.betterpvp.hub.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommandLoader;
import me.mykindos.betterpvp.hub.Hub;

@Singleton
public class BrigadierHubCommandLoader extends BrigadierCommandLoader {
    @Inject
    public BrigadierHubCommandLoader(Hub plugin) {
        super(plugin);
    }
}
