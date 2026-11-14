package me.mykindos.betterpvp.hub.commands.queue;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.orchestration.api.OrchestrationGateway;
import me.mykindos.betterpvp.orchestration.model.QueueSnapshot;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/**
 * {@code /queue view <server>} shows a server's queue.
 */
@Singleton
@BrigadierSubCommand(QueueCommand.class)
public class QueueViewCommand extends BrigadierCommand {

    private final OrchestrationGateway orchestrationGateway;

    @Inject
    public QueueViewCommand(ClientManager clientManager, OrchestrationGateway orchestrationGateway) {
        super(clientManager);
        this.orchestrationGateway = orchestrationGateway;
    }

    @Override
    public String getName() {
        return "view";
    }

    @Override
    public String getDescription() {
        return "View queue details for a server";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("hub.command.queue-view.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("server", StringArgumentType.word())
                        .executes(context -> {
                            view(context.getSource().getSender(), context.getArgument("server", String.class));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void view(CommandSender sender, String serverName) {
        try {
            final QueueSnapshot snapshot = orchestrationGateway.getQueueSnapshot(QueueCommandSupport.buildTarget(serverName)).join();
            UtilMessage.message(sender, "core.prefix.queue", "hub.queue.view.snapshot-header");
            UtilMessage.message(sender, QueueCommandSupport.buildQueueSnapshotMessage(snapshot));
        } catch (Exception ex) {
            QueueCommandSupport.logCommandFailure("load queue snapshot for " + serverName, ex);
            UtilMessage.message(sender, "core.prefix.queue", "hub.queue.view.failed");
        }
    }
}
