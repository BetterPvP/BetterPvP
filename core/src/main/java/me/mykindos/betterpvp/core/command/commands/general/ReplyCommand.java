package me.mykindos.betterpvp.core.command.commands.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Optional;
import java.util.UUID;
import me.mykindos.betterpvp.core.chat.network.PrivateMessages;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.properties.ClientProperty;
import me.mykindos.betterpvp.core.client.punishments.PunishmentTypes;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Sends a private message to whoever the executor last messaged or heard from.
 */
@Singleton
public class ReplyCommand extends BrigadierCommand {

    private final PrivateMessages privateMessages;

    @Inject
    public ReplyCommand(ClientManager clientManager, PrivateMessages privateMessages) {
        super(clientManager);
        this.privateMessages = privateMessages;
        getAliases().add("r");
    }

    @Override
    public String getName() {
        return "reply";
    }

    @Override
    public String getDescription() {
        return "Reply to another players message";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.reply.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final Client client = getClientFromExecutor(context);
                            if (client.hasPunishment(PunishmentTypes.MUTE)) {
                                UtilMessage.message(player, "core.prefix.command", "core.command.reply.muted");
                                return Command.SINGLE_SUCCESS;
                            }
                            final Optional<UUID> lastMessaged = client.getProperty(ClientProperty.LAST_MESSAGED.name());
                            if (lastMessaged.isEmpty()) {
                                UtilMessage.message(player, "core.prefix.command", "core.command.reply.no_target");
                                return Command.SINGLE_SUCCESS;
                            }
                            final String message = context.getArgument("message", String.class);
                            privateMessages.sendTo(player, client, lastMessaged.get(), message).thenAccept(result -> {
                                if (result.getOutcome() == PrivateMessages.Outcome.IGNORING) {
                                    UtilMessage.message(player, "core.prefix.command", "core.command.reply.ignored",
                                            Component.text(result.getTargetName(), NamedTextColor.YELLOW));
                                } else if (result.getOutcome() != PrivateMessages.Outcome.DELIVERED) {
                                    UtilMessage.message(player, "core.prefix.command", "core.command.reply.player_not_found");
                                }
                            });
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
