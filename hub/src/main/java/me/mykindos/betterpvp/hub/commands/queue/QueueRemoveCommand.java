package me.mykindos.betterpvp.hub.commands.queue;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.orchestration.api.OrchestrationGateway;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /queue remove <player>} takes a player out of the queue they are in.
 */
@Singleton
@BrigadierSubCommand(QueueCommand.class)
public class QueueRemoveCommand extends BrigadierCommand {

    private final OrchestrationGateway orchestrationGateway;

    @Inject
    public QueueRemoveCommand(ClientManager clientManager, OrchestrationGateway orchestrationGateway) {
        super(clientManager);
        this.orchestrationGateway = orchestrationGateway;
    }

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public String getDescription() {
        return "Remove a player from the queue";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("hub.command.queue-remove.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .executes(context -> {
                            remove(context.getSource().getSender(), context.getArgument("player", String.class));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void remove(CommandSender sender, String targetName) {
        final Client target = clientManager.search().offline(targetName).join().orElse(null);
        if (target == null) {
            UtilMessage.message(sender, "core.prefix.queue", "hub.queue.player-not-found");
            return;
        }
        try {
            if (!orchestrationGateway.removeQueuedPlayer(target.getUniqueId()).join()) {
                UtilMessage.message(sender, "core.prefix.queue", "hub.queue.not-queued",
                        Component.text(target.getName(), NamedTextColor.YELLOW));
                return;
            }
            UtilMessage.message(sender, "core.prefix.queue", "hub.queue.remove.success", Component.text(target.getName(), NamedTextColor.YELLOW));
        } catch (Exception ex) {
            QueueCommandSupport.logCommandFailure("remove queued player " + target.getName(), ex);
            UtilMessage.message(sender, "core.prefix.queue", "hub.queue.remove.failed");
        }
    }
}
