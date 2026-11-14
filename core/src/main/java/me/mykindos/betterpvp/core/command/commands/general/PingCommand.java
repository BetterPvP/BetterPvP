package me.mykindos.betterpvp.core.command.commands.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.config.Config;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Shows the executor's ping, or another online player's when staff ask (or anyone, if config allows it).
 */
@Singleton
public class PingCommand extends BrigadierCommand {

    @Inject
    @Config(path = "ping.onlyStaffCanSeeOthers", defaultValue = "true")
    private boolean onlyStaffCanSeeOthers;

    @Inject
    public PingCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public String getDescription() {
        return "Show your ping";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.ping.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    UtilMessage.message(player, "core.prefix.command", "core.command.ping.self", formatPing(UtilPlayer.getPing(player)));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName(),
                                source -> !onlyStaffCanSeeOthers || senderHasRank(source, Rank.MODERATOR))
                        .executes(context -> {
                            final String targetName = context.getArgument("player", String.class);
                            clientManager.search(context.getSource().getSender()).online(targetName).ifPresent(targetClient -> {
                                final Player targetPlayer = targetClient.getGamer().getPlayer();
                                if (targetPlayer == null) {
                                    return;
                                }
                                UtilMessage.message(context.getSource().getSender(), "core.prefix.command", "core.command.ping.other",
                                        Component.text(targetClient.getName(), NamedTextColor.YELLOW),
                                        formatPing(UtilPlayer.getPing(targetPlayer)));
                            });
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private Component formatPing(final int ping) {
        NamedTextColor color = NamedTextColor.DARK_RED;
        if (ping <= 0) {
            color = NamedTextColor.WHITE;
        } else if (ping < 25) {
            color = NamedTextColor.DARK_GREEN;
        } else if (ping < 50) {
            color = NamedTextColor.GREEN;
        } else if (ping < 100) {
            color = NamedTextColor.GOLD;
        } else if (ping < 200) {
            color = NamedTextColor.YELLOW;
        } else if (ping < 300) {
            color = NamedTextColor.RED;
        }
        return Component.text(ping).color(color).append(Component.text("ms").color(NamedTextColor.GRAY));
    }
}
