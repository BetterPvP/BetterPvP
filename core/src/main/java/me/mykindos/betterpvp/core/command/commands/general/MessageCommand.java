package me.mykindos.betterpvp.core.command.commands.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import me.mykindos.betterpvp.core.chat.network.PrivateMessages;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.punishments.PunishmentTypes;
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
 * Sends a private message to a player, unless the executor is muted.
 */
@Singleton
public class MessageCommand extends BrigadierCommand {

    private final PrivateMessages privateMessages;

    @Inject
    public MessageCommand(ClientManager clientManager, PrivateMessages privateMessages) {
        super(clientManager);
        this.privateMessages = privateMessages;
        getAliases().addAll(List.of("m", "msg", "tell", "whisper", "w"));
    }

    @Override
    public String getName() {
        return "message";
    }

    @Override
    public String getDescription() {
        return "Send a message to another player";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.message.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .then(BrigadierNodes.argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    final Player player = getPlayerFromExecutor(context);
                                    final Client client = getClientFromExecutor(context);
                                    if (client.hasPunishment(PunishmentTypes.MUTE)) {
                                        UtilMessage.message(player, "core.prefix.command", "core.command.message.muted");
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    final String targetName = context.getArgument("player", String.class);
                                    final String message = context.getArgument("message", String.class);
                                    privateMessages.send(player, client, targetName, message).thenAccept(result -> {
                                        switch (result.getOutcome()) {
                                            case NOT_FOUND -> UtilMessage.message(player, "core.prefix.command", "core.command.message.player_not_found");
                                            case SELF -> UtilMessage.message(player, "core.prefix.command", "core.command.message.self");
                                            case IGNORING -> UtilMessage.message(player, "core.prefix.command", "core.command.message.ignored",
                                                    Component.text(result.getTargetName(), NamedTextColor.YELLOW));
                                            case DELIVERED -> {
                                            }
                                        }
                                    });
                                    return Command.SINGLE_SUCCESS;
                                })));
    }
}
