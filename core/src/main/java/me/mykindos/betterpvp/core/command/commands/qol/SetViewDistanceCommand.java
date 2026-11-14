package me.mykindos.betterpvp.core.command.commands.qol;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Sets the view and simulation distance of the executor's world, from 3 to 32 chunks.
 */
@Singleton
public class SetViewDistanceCommand extends BrigadierCommand {

    @Inject
    public SetViewDistanceCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "setviewdistance";
    }

    @Override
    public String getDescription() {
        return "Set the view distance of your current world";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.set-view-distance.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("distance", IntegerArgumentType.integer(3, 32))
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final int distance = context.getArgument("distance", Integer.class);
                            player.getWorld().setViewDistance(distance);
                            player.getWorld().setSimulationDistance(distance);
                            UtilMessage.message(player, "core.prefix.command", "core.command.setviewdistance.success",
                                    Component.text(distance, NamedTextColor.YELLOW));
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
