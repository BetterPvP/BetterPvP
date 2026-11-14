package me.mykindos.betterpvp.core.command.commands.admin.zone;

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

/**
 * Root {@code /zone} command for zone tooling. Holds the {@code list} subcommand and the {@code discovery} group.
 */
@Singleton
public class ZoneCommand extends BrigadierCommand {

    @Inject
    public ZoneCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "zone";
    }

    @Override
    public String getDescription() {
        return "Zone management and discovery tooling";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.zone.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), "core.prefix.zones", "core.command.zone.usage");
                    return Command.SINGLE_SUCCESS;
                });
    }
}
