package me.mykindos.betterpvp.core.framework.display.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/**
 * Display entity editing tools: {@code summon}, {@code select}, {@code deselect}, {@code remove}, {@code transform} and {@code interpolation}.
 */
@Singleton
public class DisplayCommand extends BrigadierCommand {

    @Inject
    public DisplayCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "display";
    }

    @Override
    public String getDescription() {
        return "Modify a block display";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.display.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final CommandSender sender = context.getSource().getSender();
                    UtilMessage.message(sender, "core.prefix.display", "core.display.help.header");
                    UtilMessage.message(sender, "core.prefix.display", "core.display.help.summon");
                    UtilMessage.message(sender, "core.prefix.display", "core.display.help.select");
                    UtilMessage.message(sender, "core.prefix.display", "core.display.help.deselect");
                    UtilMessage.message(sender, "core.prefix.display", "core.display.help.remove");
                    UtilMessage.message(sender, "core.prefix.display", "core.display.help.transform");
                    return Command.SINGLE_SUCCESS;
                });
    }
}
