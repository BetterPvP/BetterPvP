package me.mykindos.betterpvp.core.client.punishments.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * A {@code /legacypunish} subcommand. Its arguments are positional words, rules, types and times mixed with a free
 * reason, so the rest of the line is handed over as words, the way the console path also reads them.
 */
public abstract class PunishmentSubCommand extends BrigadierCommand {

    protected PunishmentSubCommand(ClientManager clientManager) {
        super(clientManager);
    }

    /**
     * Runs for a player executor.
     */
    protected abstract void execute(Player player, Client client, String... args);

    /**
     * Runs for any other sender, such as the console.
     */
    protected abstract void execute(CommandSender sender, String[] args);

    /**
     * Completions for the word being typed, given every word so far.
     */
    protected abstract List<String> suggestions(CommandSender sender, String[] args);

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> run(context, new String[0]))
                .then(BrigadierNodes.argument("arguments", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            final String typed = builder.getRemaining();
                            final SuggestionsBuilder next = builder.createOffset(builder.getStart() + typed.lastIndexOf(' ') + 1);
                            suggestions(context.getSource().getSender(), typed.split(" ", -1)).forEach(next::suggest);
                            return next.buildFuture();
                        })
                        .executes(context -> run(context, context.getArgument("arguments", String.class).trim().split("\\s+"))));
    }

    private int run(CommandContext<CommandSourceStack> context, String[] args) throws CommandSyntaxException {
        if (context.getSource().getExecutor() instanceof Player player) {
            execute(player, getClientFromExecutor(context), args);
        } else {
            execute(context.getSource().getSender(), args);
        }
        return Command.SINGLE_SUCCESS;
    }
}
