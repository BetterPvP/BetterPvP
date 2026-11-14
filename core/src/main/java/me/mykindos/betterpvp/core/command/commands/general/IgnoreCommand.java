package me.mykindos.betterpvp.core.command.commands.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Toggles whether the executor ignores private messages from a player.
 */
@Singleton
public class IgnoreCommand extends BrigadierCommand {

    @Inject
    public IgnoreCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "ignore";
    }

    @Override
    public String getDescription() {
        return "Ignore private messages from a player";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.ignore.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final Client client = getClientFromExecutor(context);
                            final String targetName = context.getArgument("player", String.class);
                            clientManager.search().offline(targetName).thenAcceptAsync(targetOptional -> {
                                if (targetOptional.isEmpty()) {
                                    UtilMessage.message(player, "core.prefix.ignore", "core.command.ignore.player_not_found",
                                            Component.text(targetName, NamedTextColor.YELLOW));
                                    return;
                                }
                                final Client target = targetOptional.get();
                                if (client.getIgnores().contains(target.getUniqueId())) {
                                    clientManager.removeIgnore(client, target);
                                    UtilMessage.message(player, "core.prefix.ignore", "core.command.ignore.remove.success",
                                            Component.text(target.getName(), NamedTextColor.YELLOW));
                                    return;
                                }
                                clientManager.saveIgnore(client, target);
                                UtilMessage.message(player, "core.prefix.ignore", "core.command.ignore.add.success",
                                        Component.text(target.getName(), NamedTextColor.YELLOW));
                            });
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
