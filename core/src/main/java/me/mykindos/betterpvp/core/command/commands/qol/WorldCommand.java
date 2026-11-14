package me.mykindos.betterpvp.core.command.commands.qol;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.framework.chat.ChatCallbacks;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.world.WorldHandler;
import me.mykindos.betterpvp.core.world.menu.GuiWorldManager;
import net.kyori.adventure.text.Component;

/**
 * Opens the world manager menu.
 */
@Singleton
public class WorldCommand extends BrigadierCommand {

    private final WorldHandler worldHandler;
    private final ChatCallbacks callbacks;

    @Inject
    public WorldCommand(ClientManager clientManager, WorldHandler worldHandler, ChatCallbacks callbacks) {
        super(clientManager);
        this.worldHandler = worldHandler;
        this.callbacks = callbacks;
    }

    @Override
    public String getName() {
        return "world";
    }

    @Override
    public String getDescription() {
        return "Use and administrate the server's worlds.";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.world.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    new GuiWorldManager(worldHandler, callbacks, null).show(getPlayerFromExecutor(context));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
