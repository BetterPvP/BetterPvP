package me.mykindos.betterpvp.hub.commands.queue;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.hub.feature.queue.HubQueueStatusRegistry;
import me.mykindos.betterpvp.orchestration.model.QueueStatusUpdate;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Shows the executor's place in the queue they are in. Its subcommands leave the queue and manage server queues.
 */
@Singleton
public class QueueCommand extends BrigadierCommand {

    private final HubQueueStatusRegistry queueStatusRegistry;

    @Inject
    public QueueCommand(ClientManager clientManager, HubQueueStatusRegistry queueStatusRegistry) {
        super(clientManager);
        this.queueStatusRegistry = queueStatusRegistry;
    }

    @Override
    public String getName() {
        return "queue";
    }

    @Override
    public String getDescription() {
        return "View or manage queue state";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("hub.command.queue.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    final QueueStatusUpdate status = queueStatusRegistry.getStatus(player.getUniqueId()).orElse(null);
                    if (status == null) {
                        UtilMessage.message(player, "core.prefix.queue", "hub.queue.not-queued-any");
                        return Command.SINGLE_SUCCESS;
                    }
                    UtilMessage.message(player, "core.prefix.queue", "hub.queue.status-header");
                    UtilMessage.message(player, QueueCommandSupport.buildQueueMessage(status));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
