package me.mykindos.betterpvp.core.stats.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Optional;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.stats.Leaderboard;
import me.mykindos.betterpvp.core.stats.menu.LeaderboardCategoryMenu;
import me.mykindos.betterpvp.core.stats.menu.LeaderboardMenu;
import me.mykindos.betterpvp.core.stats.repository.LeaderboardManager;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * {@code /leaderboard} opens the leaderboard categories, and {@code /leaderboard <name>} opens one leaderboard.
 */
@Singleton
public class LeaderboardCommand extends BrigadierCommand {

    private final LeaderboardManager leaderboards;

    @Inject
    public LeaderboardCommand(ClientManager clientManager, LeaderboardManager leaderboards) {
        super(clientManager);
        this.leaderboards = leaderboards;
        getAliases().add("lb");
        getAliases().add("top");
    }

    @Override
    public String getName() {
        return "leaderboard";
    }

    @Override
    public String getDescription() {
        return "Leaderboard base command";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.leaderboard.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    new LeaderboardCategoryMenu(leaderboards).show(getPlayerFromExecutor(context));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("leaderboard", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            leaderboards.getViewable().keySet().stream()
                                    .filter(name -> name.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final String name = context.getArgument("leaderboard", String.class);
                            final Optional<Leaderboard<?, ?>> leaderboard = leaderboards.getViewableByName(name);
                            if (leaderboard.isEmpty() || !leaderboard.get().isViewable()) {
                                UtilMessage.message(player, "core.prefix.stats", "core.command.leaderboard.not_found",
                                        Component.text(name, NamedTextColor.YELLOW));
                                return Command.SINGLE_SUCCESS;
                            }
                            new LeaderboardMenu<>(player, leaderboard.get(), null).show(player);
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
