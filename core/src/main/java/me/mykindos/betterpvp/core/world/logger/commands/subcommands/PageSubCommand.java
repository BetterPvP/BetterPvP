package me.mykindos.betterpvp.core.world.logger.commands.subcommands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.world.logger.WorldLogHandler;
import me.mykindos.betterpvp.core.world.logger.WorldLogSession;
import me.mykindos.betterpvp.core.world.logger.commands.WorldLoggerCommand;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /worldlogger page <page>} shows one page of the executor's current lookup.
 */
@Singleton
@BrigadierSubCommand(WorldLoggerCommand.class)
public class PageSubCommand extends BrigadierCommand {

    private final WorldLogHandler worldLogHandler;

    @Inject
    public PageSubCommand(ClientManager clientManager, WorldLogHandler worldLogHandler) {
        super(clientManager);
        this.worldLogHandler = worldLogHandler;
    }

    @Override
    public String getName() {
        return "page";
    }

    @Override
    public String getDescription() {
        return "View results of a specific page in your session";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.page.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final int page = context.getArgument("page", Integer.class);
                            final WorldLogSession session = worldLogHandler.getSession(player.getUniqueId());
                            if (session == null || page > session.getPages()) {
                                UtilMessage.message(player, "core.prefix.command", "core.command.worldlogger.page.invalid");
                                return Command.SINGLE_SUCCESS;
                            }
                            session.setCurrentPage(page);
                            worldLogHandler.displayResults(player, session, page);
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
