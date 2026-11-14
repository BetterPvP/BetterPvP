package me.mykindos.betterpvp.core.world.logger.commands.subcommands;

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
import me.mykindos.betterpvp.core.world.logger.WorldLogHandler;
import me.mykindos.betterpvp.core.world.logger.commands.WorldLoggerCommand;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /worldlogger inspect} toggles inspection mode, where clicking a block shows its logged changes.
 */
@Singleton
@BrigadierSubCommand(WorldLoggerCommand.class)
public class InspectSubCommand extends BrigadierCommand {

    private final WorldLogHandler worldLogHandler;

    @Inject
    public InspectSubCommand(ClientManager clientManager, WorldLogHandler worldLogHandler) {
        super(clientManager);
        this.worldLogHandler = worldLogHandler;
    }

    @Override
    public String getName() {
        return "inspect";
    }

    @Override
    public String getDescription() {
        return "Enable inspection mode";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.inspect.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    if (worldLogHandler.getInspectingPlayers().remove(player.getUniqueId())) {
                        UtilMessage.message(player, "core.prefix.command", "core.command.worldlogger.inspect.disabled");
                    } else {
                        worldLogHandler.getInspectingPlayers().add(player.getUniqueId());
                        UtilMessage.message(player, "core.prefix.command", "core.command.worldlogger.inspect.enabled");
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }
}
