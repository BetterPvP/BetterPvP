package me.mykindos.betterpvp.clans.clans.commands.subcommands.brigadier.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Optional;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.commands.BrigadierClansCommand;
import me.mykindos.betterpvp.clans.commands.arguments.BPvPClansArgumentTypes;
import me.mykindos.betterpvp.clans.commands.commands.ClanBrigadierCommand;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.logging.LogContext;
import me.mykindos.betterpvp.core.logging.menu.CachedLogMenu;
import me.mykindos.betterpvp.core.logging.repository.LogRepository;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Opens the logs of any clan by name, or of a disbanded clan by its id. Needs no clan of its own.
 */
@Singleton
@BrigadierSubCommand(BrigadierClansCommand.class)
public class BrigadierAdminLogsCommand extends ClanBrigadierCommand {
    private final LogRepository logRepository;

    @Inject
    protected BrigadierAdminLogsCommand(ClientManager clientManager, ClanManager clanManager, LogRepository logRepository) {
        super(clientManager, clanManager);
        this.logRepository = logRepository;
    }

    @Override
    public String getName() {
        return "adminlogs";
    }

    @Override
    public String getDescription() {
        return "Get logs associated with the specified clan";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.admin-logs.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("clan", StringArgumentType.string())
                        .suggests((context, builder) -> BPvPClansArgumentTypes.clan().listSuggestions(context, builder))
                        .executes(context -> {
                            final Player executor = getPlayerFromExecutor(context);
                            final String input = context.getArgument("clan", String.class);
                            final Optional<String> clanId = clanId(input);
                            if (clanId.isEmpty()) {
                                UtilMessage.message(executor, "clans.prefix", "clans.command.clan.admin-logs.invalid-id",
                                        Component.text(input, NamedTextColor.YELLOW));
                                return Command.SINGLE_SUCCESS;
                            }
                            final String title = clanManager.getClanByName(input).map(Clan::getName).orElse(input);
                            new CachedLogMenu(title, LogContext.CLAN, clanId.get(), null, CachedLogMenu.CLANS,
                                    JavaPlugin.getPlugin(Clans.class), logRepository, null).show(executor);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    /**
     * @param input a clan name, or the id of a clan, disbanded ones included
     * @return the clan id the logs are kept under, or empty when the input is neither
     */
    Optional<String> clanId(String input) {
        final Optional<Clan> clan = clanManager.getClanByName(input);
        if (clan.isPresent()) {
            return Optional.of(Long.toString(clan.get().getId()));
        }
        try {
            return Optional.of(Long.toString(Long.parseLong(input)));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
