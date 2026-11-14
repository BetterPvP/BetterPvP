package me.mykindos.betterpvp.core.command.commands.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.Objects;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;

/**
 * Kicks the one online player a name matches, with a reason, and announces it to the server.
 */
@Singleton
public class KickCommand extends BrigadierCommand {

    @Inject
    public KickCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "kick";
    }

    @Override
    public String getDescription() {
        return "Kick a player from the server";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.kick.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .then(BrigadierNodes.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> {
                                    final CommandSender sender = context.getSource().getSender();
                                    final Collection<Client> matches = clientManager.search(sender)
                                            .advancedOnline(context.getArgument("player", String.class));
                                    if (matches.size() != 1) {
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    final Client target = matches.iterator().next();
                                    final Player toKick = Objects.requireNonNull(target.getGamer().getPlayer());
                                    final String reason = context.getArgument("reason", String.class);
                                    toKick.kick(Translations.component("core.command.kick.screen",
                                            Translations.component("core.command.kick.screen_label").color(NamedTextColor.RED),
                                            Component.text(reason, NamedTextColor.GRAY)), PlayerKickEvent.Cause.KICK_COMMAND);
                                    UtilMessage.broadcast("core.prefix.command", "core.command.kick.broadcast",
                                            Component.text(sender.getName(), NamedTextColor.YELLOW),
                                            Component.text(target.getName(), NamedTextColor.YELLOW),
                                            Component.text(reason, NamedTextColor.YELLOW));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }
}
