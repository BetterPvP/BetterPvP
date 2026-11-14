package me.mykindos.betterpvp.clans.clans.commands.subcommands.brigadier.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.commands.BrigadierClansCommand;
import me.mykindos.betterpvp.clans.commands.commands.ClanBrigadierCommand;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

@Singleton
@BrigadierSubCommand(BrigadierClansCommand.class)
public class BrigadierHelpSubCommand extends ClanBrigadierCommand {
    private static final int NUMPERPAGE = 5;


    @Inject
    protected BrigadierHelpSubCommand(ClientManager clientManager, ClanManager clanManager) {
        super(clientManager, clanManager);
        this.getAliases().addAll(List.of("?", "h"));
    }

    /**
     * Used in retrieving path for config options
     *
     * @return the name of this command
     */
    @Override
    public String getName() {
        return "help";
    }

    /**
     * Gets the description of this command, used in registration
     *
     * @return the description
     */
    @Override
    public String getDescription() {
        return "List all available commands.";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.clan-help.description");
    }

    /**
     * Define the command, using normal rank based permissions
     * Requires sender to have required rank and executor to be a player
     *
     * @return the builder to be used in Build
     */
    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    doHelp(context, "", 1);
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            int page = context.getArgument("page", Integer.class);
                            doHelp(context, "", page);
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(BrigadierNodes.argument("filter", StringArgumentType.word())
                        .executes(context -> {
                            String filter = context.getArgument("filter", String.class);
                            doHelp(context, filter, 1);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(BrigadierNodes.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    String filter = context.getArgument("filter", String.class);
                                    int page = context.getArgument("page", Integer.class);
                                    doHelp(context, filter, page);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                );
    }

    private void doHelp(CommandContext<CommandSourceStack> context, String filter, int page) throws CommandSyntaxException {
        final Player player = getPlayerFromExecutor(context);


        final BrigadierCommand parent = getParent();
        List<BrigadierCommand> childCommands = Objects.requireNonNull(parent).getChildren().stream()
                .filter(child -> child.getName().toLowerCase().contains(filter.toLowerCase()))
                .filter(child -> senderHasRank(context.getSource(), child.getRequiredRank()))
                .sorted(Comparator.comparing(BrigadierCommand::getName))
                .toList();


        final int totalPages = pageCount(childCommands.size(), NUMPERPAGE);
        Component component = Translations.component("clans.command.clan.help.header").color(NamedTextColor.YELLOW)
                .append(Component.text(page + " / " + totalPages, NamedTextColor.WHITE));
        for (BrigadierCommand subCommand : page(childCommands, page, NUMPERPAGE)) {
            component = component.append(addHelpCommandComponent(context, subCommand));
        }
        UtilMessage.message(player, "clans.prefix", component);

    }

    private Component addHelpCommandComponent(CommandContext<CommandSourceStack> context, BrigadierCommand command) throws CommandSyntaxException {
        NamedTextColor color = command.requirement(context.getSource()) ? NamedTextColor.GREEN : NamedTextColor.RED;
        Component commandComponent = Component.empty();
        commandComponent = commandComponent.appendNewline()
                .append(Component.text(command.getName(), color)
                        .hoverEvent(HoverEvent.showText(command.getRequirementComponent(context)))
                        .clickEvent(ClickEvent.suggestCommand("/c " + command.getName()))
                ).appendSpace()
                .append(command.getDescriptionComponent().color(NamedTextColor.WHITE)).appendNewline()
                .append(Translations.component("clans.command.clan.help.usage-prefix").color(NamedTextColor.GOLD));

                if (command.requirement(context.getSource())) {
                    commandComponent = commandComponent.append(Component.text(command.getUsages(context.getSource(), "c"), NamedTextColor.WHITE));
                } else {
                    commandComponent = commandComponent.append(Component.text(command.getUsages(context.getSource(), "c"), NamedTextColor.WHITE)
                            .hoverEvent(HoverEvent.showText(Translations.component("clans.command.clan.help.usage-incomplete").color(NamedTextColor.WHITE)))
                    );
                }

        return commandComponent;
    }
}
