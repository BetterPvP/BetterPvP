package me.mykindos.betterpvp.hub.commands.queue;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.hub.feature.queue.HubQueueStatusRegistry;
import me.mykindos.betterpvp.orchestration.api.OrchestrationGateway;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * {@code /queue leave} takes the executor out of the queue they are in.
 */
@Singleton
@BrigadierSubCommand(QueueCommand.class)
public class QueueLeaveCommand extends BrigadierCommand {

    private final OrchestrationGateway orchestrationGateway;
    private final HubQueueStatusRegistry queueStatusRegistry;

    @Inject
    public QueueLeaveCommand(ClientManager clientManager, OrchestrationGateway orchestrationGateway, HubQueueStatusRegistry queueStatusRegistry) {
        super(clientManager);
        this.orchestrationGateway = orchestrationGateway;
        this.queueStatusRegistry = queueStatusRegistry;
    }

    @Override
    public String getName() {
        return "leave";
    }

    @Override
    public String getDescription() {
        return "Leave the queue you are currently in";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("hub.command.queue-leave.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    if (queueStatusRegistry.getStatus(player.getUniqueId()).isEmpty()) {
                        UtilMessage.message(player, "core.prefix.queue", "hub.queue.not-queued-any");
                        return Command.SINGLE_SUCCESS;
                    }
                    try {
                        orchestrationGateway.leaveQueue(player.getUniqueId()).join();
                        UtilMessage.message(player, "core.prefix.queue", "hub.queue.leave.success");
                    } catch (Exception ex) {
                        QueueCommandSupport.logCommandFailure("leave queue for " + player.getName(), ex);
                        UtilMessage.message(player, "core.prefix.queue", "hub.queue.leave.failed");
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }
}
