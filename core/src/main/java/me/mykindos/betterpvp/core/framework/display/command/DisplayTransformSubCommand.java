package me.mykindos.betterpvp.core.framework.display.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;

/**
 * {@code /display transform} groups {@code rotate}, {@code scale} and {@code translate}.
 */
@Singleton
@BrigadierSubCommand(DisplayCommand.class)
public class DisplayTransformSubCommand extends BrigadierCommand {

    @Inject
    public DisplayTransformSubCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "transform";
    }

    @Override
    public String getDescription() {
        return "Transform a display entity";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.display-transform.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), "core.prefix.display", "core.display.transform.usage");
                    return Command.SINGLE_SUCCESS;
                });
    }
}
