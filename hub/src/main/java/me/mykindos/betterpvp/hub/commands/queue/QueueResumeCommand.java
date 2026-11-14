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
import me.mykindos.betterpvp.orchestration.model.QueueState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /queue resume <server>} lets a paused server admit players from its queue again.
 */
@Singleton
@BrigadierSubCommand(QueueCommand.class)
public class QueueResumeCommand extends BrigadierCommand {

    private final OrchestrationGateway orchestrationGateway;

    @Inject
    public QueueResumeCommand(ClientManager clientManager, OrchestrationGateway orchestrationGateway) {
        super(clientManager);
        this.orchestrationGateway = orchestrationGateway;
    }

    @Override
    public String getName() {
        return "resume";
    }

    @Override
    public String getDescription() {
        return "Re-open queue admissions for a server";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("hub.command.queue-resume.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("server", StringArgumentType.word())
                        .executes(context -> {
                            setState(context.getSource().getSender(), context.getArgument("server", String.class));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void setState(CommandSender sender, String serverName) {
        try {
            orchestrationGateway.setQueueState(QueueCommandSupport.buildTarget(serverName), QueueState.OPEN).join();
            UtilMessage.message(sender, "core.prefix.queue", "hub.queue.state-set",
                    Component.text(serverName, NamedTextColor.YELLOW),
                    Component.text(QueueState.OPEN.name(), NamedTextColor.YELLOW));
        } catch (Exception ex) {
            QueueCommandSupport.logCommandFailure("update queue state for " + serverName + " to OPEN", ex);
            UtilMessage.message(sender, "core.prefix.queue", "hub.queue.state-set.failed");
        }
    }
}
