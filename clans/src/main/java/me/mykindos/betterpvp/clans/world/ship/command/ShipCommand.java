package me.mykindos.betterpvp.clans.world.ship.command;

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
 * Root {@code /ship} command for builder tooling on moored vessels. Holds {@code list}, {@code disable} and
 * {@code enable}, the way to see the dock underneath a hull without editing the map.
 */
@Singleton
public class ShipCommand extends BrigadierCommand {

    @Inject
    public ShipCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "ship";
    }

    @Override
    public String getDescription() {
        return "Inspect and temporarily un-moor the ships in this world";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.ship.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), "clans.prefix.ships", "clans.command.ship.usage");
                    return Command.SINGLE_SUCCESS;
                });
    }
}
