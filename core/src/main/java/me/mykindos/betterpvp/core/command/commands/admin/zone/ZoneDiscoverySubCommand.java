package me.mykindos.betterpvp.core.command.commands.admin.zone;

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
 * {@code /zone discovery <list|add|remove|reset> ...} groups the zone-discovery management subcommands.
 */
@Singleton
@BrigadierSubCommand(ZoneCommand.class)
public class ZoneDiscoverySubCommand extends BrigadierCommand {

    @Inject
    public ZoneDiscoverySubCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "discovery";
    }

    @Override
    public String getDescription() {
        return "Manage players' zone discoveries";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.zone.discovery.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), "core.prefix.zones", "core.command.zone.discovery.usage");
                    return Command.SINGLE_SUCCESS;
                });
    }
}
