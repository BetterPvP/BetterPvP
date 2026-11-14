package me.mykindos.betterpvp.core.command.commands.admin;

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
import me.mykindos.betterpvp.core.logging.repository.LogRepository;
import net.kyori.adventure.text.Component;

/**
 * Deletes logs older than a number of days that have no action, up to a limit, where 0 means 100,000.
 */
@Singleton
public class LogPurgeCommand extends BrigadierCommand {

    private final LogRepository logRepository;

    @Inject
    public LogPurgeCommand(ClientManager clientManager, LogRepository logRepository) {
        super(clientManager);
        this.logRepository = logRepository;
    }

    @Override
    public String getName() {
        return "logpurge";
    }

    @Override
    public String getDescription() {
        return "Deletes all logs that don't have an associated action";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.log-purge.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("days", IntegerArgumentType.integer(0))
                        .then(BrigadierNodes.argument("limit", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    final int limit = context.getArgument("limit", Integer.class);
                                    logRepository.purgeLogs(context.getArgument("days", Integer.class), limit == 0 ? 100_000 : limit);
                                    return Command.SINGLE_SUCCESS;
                                })));
    }
}
