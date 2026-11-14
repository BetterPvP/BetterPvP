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
 * {@code /queue admit <player>} lets a queued player in next.
 */
@Singleton
@BrigadierSubCommand(QueueCommand.class)
public class QueueAdmitCommand extends BrigadierCommand {

    private final OrchestrationGateway orchestrationGateway;

    @Inject
    public QueueAdmitCommand(ClientManager clientManager, OrchestrationGateway orchestrationGateway) {
        super(clientManager);
        this.orchestrationGateway = orchestrationGateway;
    }

    @Override
    public String getName() {
        return "admit";
    }

    @Override
    public String getDescription() {
        return "Force the next admission for a queued player";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("hub.command.queue-admit.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .executes(context -> {
                            admit(context.getSource().getSender(), context.getArgument("player", String.class));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void admit(CommandSender sender, String targetName) {
        final Client target = clientManager.search().offline(targetName).join().orElse(null);
        if (target == null) {
            UtilMessage.message(sender, "core.prefix.queue", "hub.queue.player-not-found");
            return;
        }
        try {
            if (!orchestrationGateway.admitQueuedPlayer(target.getUniqueId()).join()) {
                UtilMessage.message(sender, "core.prefix.queue", "hub.queue.not-queued",
                        Component.text(target.getName(), NamedTextColor.YELLOW));
                return;
            }
            UtilMessage.message(sender, "core.prefix.queue", "hub.queue.admit.success", Component.text(target.getName(), NamedTextColor.YELLOW));
        } catch (Exception ex) {
            QueueCommandSupport.logCommandFailure("admit queued player " + target.getName(), ex);
            UtilMessage.message(sender, "core.prefix.queue", "hub.queue.admit.failed");
        }
    }
}
