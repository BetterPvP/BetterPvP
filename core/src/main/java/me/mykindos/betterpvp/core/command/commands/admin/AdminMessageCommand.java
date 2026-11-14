package me.mykindos.betterpvp.core.command.commands.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.framework.server.CrossServerMessageService;
import me.mykindos.betterpvp.core.framework.server.ServerMessage;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.core.utilities.model.SoundEffect;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Sends a staff message to a player anywhere on the network. A name that matches no one online here is looked up
 * across the network before it counts as no match.
 */
@Singleton
public class AdminMessageCommand extends BrigadierCommand {

    private final Core core;
    private final CrossServerMessageService crossServerMessageService;

    @Inject
    public AdminMessageCommand(ClientManager clientManager, Core core, CrossServerMessageService crossServerMessageService) {
        super(clientManager);
        this.core = core;
        this.crossServerMessageService = crossServerMessageService;
        getAliases().add("ma");
    }

    @Override
    public String getName() {
        return "messageadmin";
    }

    @Override
    public String getDescription() {
        return "Send an admin message to a player";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.admin-message.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .then(BrigadierNodes.argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    final Player player = getPlayerFromExecutor(context);
                                    final Gamer gamer = getClientFromExecutor(context).getGamer();
                                    final String targetName = context.getArgument("player", String.class);
                                    final String message = context.getArgument("message", String.class);
                                    final var search = clientManager.search(player);

                                    // Handles zero matches
                                    final Collection<Client> matches = search.inform(false).advancedOnline(targetName);
                                    if (matches.size() > 1) {
                                        search.tooManyMatches(matches, targetName);
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    if (matches.size() == 1) {
                                        sendMessage(player, gamer, matches.iterator().next(), message);
                                        return Command.SINGLE_SUCCESS;
                                    }

                                    // Not silent: an unknown name shows zero matches
                                    clientManager.search(player).offline(targetName).thenAccept(targetOptional -> {
                                        if (targetOptional.isEmpty()) {
                                            return;
                                        }
                                        final Client target = targetOptional.get();
                                        crossServerMessageService.isPlayerOnline(target.getName()).thenAccept(isOnline ->
                                                UtilServer.runTask(core, () -> {
                                                    if (!isOnline) {
                                                        search.zeroMatches(targetName);
                                                        return;
                                                    }
                                                    sendMessage(player, gamer, target, message);
                                                }));
                                    });
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private void sendMessage(Player player, Gamer gamer, Client receiver, String message) {
        gamer.setLastAdminMessenger(receiver.getUuid());
        crossServerMessageService.broadcast(ServerMessage.builder()
                .channel("AdminDirectMessage")
                .message(message)
                .metadata("sender", player.getUniqueId().toString())
                .metadata("target", receiver.getUniqueId().toString())
                .build());
        new SoundEffect("minecraft", "block.amethyst_block.resonate", 1.0F).play(player);
    }
}
