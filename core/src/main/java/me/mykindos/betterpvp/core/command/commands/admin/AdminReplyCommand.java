package me.mykindos.betterpvp.core.command.commands.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.UUID;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.framework.server.CrossServerMessageService;
import me.mykindos.betterpvp.core.framework.server.ServerMessage;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.core.utilities.model.SoundEffect;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Sends a staff message to whoever the executor last exchanged staff messages with, if they are still online.
 */
@Singleton
public class AdminReplyCommand extends BrigadierCommand {

    private final Core core;
    private final CrossServerMessageService crossServerMessageService;

    @Inject
    public AdminReplyCommand(ClientManager clientManager, Core core, CrossServerMessageService crossServerMessageService) {
        super(clientManager);
        this.core = core;
        this.crossServerMessageService = crossServerMessageService;
        getAliases().add("ra");
    }

    @Override
    public String getName() {
        return "replyadmin";
    }

    @Override
    public String getDescription() {
        return "Send an admin message to a player";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.admin-reply.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), "core.prefix.core", "core.command.adminreply.message_required");
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final Gamer gamer = getClientFromExecutor(context).getGamer();
                            if (gamer.getLastAdminMessenger() == null) {
                                UtilMessage.message(player, "core.prefix.core", "core.command.adminreply.no_previous_target");
                                return Command.SINGLE_SUCCESS;
                            }
                            final String message = context.getArgument("message", String.class);
                            clientManager.search(player).offline(UUID.fromString(gamer.getLastAdminMessenger())).thenAccept(receiver -> {
                                if (receiver.isEmpty()) {
                                    UtilServer.runTask(core, () ->
                                            UtilMessage.message(player, "core.prefix.core", "core.command.adminreply.no_online_target"));
                                    return;
                                }
                                final Client receivingClient = receiver.get();
                                crossServerMessageService.isPlayerOnline(receivingClient.getName()).thenAccept(isOnline ->
                                        UtilServer.runTask(core, () -> {
                                            if (!isOnline) {
                                                UtilMessage.message(player, "core.prefix.core", "core.command.adminreply.no_online_target");
                                                return;
                                            }
                                            gamer.setLastAdminMessenger(receivingClient.getUuid());
                                            crossServerMessageService.broadcast(ServerMessage.builder()
                                                    .channel("AdminDirectMessage")
                                                    .message(message)
                                                    .metadata("sender", player.getUniqueId().toString())
                                                    .metadata("target", receivingClient.getUniqueId().toString())
                                                    .build());
                                            new SoundEffect("minecraft", "block.amethyst_block.resonate", 1.0F).play(player);
                                        }));
                            });
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
