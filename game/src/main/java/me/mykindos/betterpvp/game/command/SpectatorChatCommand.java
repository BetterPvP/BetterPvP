package me.mykindos.betterpvp.game.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.chat.channels.ChatChannel;
import me.mykindos.betterpvp.core.chat.events.ChatSentEvent;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilFormat;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.game.GamePlugin;
import me.mykindos.betterpvp.game.framework.model.player.PlayerController;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sends one message to spectator chat, or toggles the executor's chat channel between spectator and server. Only
 * spectators can use it.
 */
@Singleton
public class SpectatorChatCommand extends BrigadierCommand {

    private final PlayerController playerController;

    @Inject
    public SpectatorChatCommand(ClientManager clientManager, PlayerController playerController) {
        super(clientManager);
        this.playerController = playerController;
        getAliases().add("specchat");
    }

    @Override
    public String getName() {
        return "spectatorchat";
    }

    @Override
    public String getDescription() {
        return "Toggle spectator only chat";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("game.command.spectator-chat.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    if (isSpectating(player)) {
                        final Gamer gamer = getClientFromExecutor(context).getGamer();
                        gamer.setChatChannel(gamer.getChatChannel().equals(playerController.getSpectatorChatChannel())
                                ? ChatChannel.SERVER : ChatChannel.SPECTATOR);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            if (isSpectating(player)) {
                                UtilServer.callEventAsync(JavaPlugin.getPlugin(GamePlugin.class), new ChatSentEvent(player,
                                        playerController.getSpectatorChatChannel(), Component.text(UtilFormat.spoofNameForLunar(player.getName()) + ": "),
                                        Component.text(context.getArgument("message", String.class))));
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private boolean isSpectating(Player player) {
        if (!playerController.getParticipant(player).isSpectating()) {
            UtilMessage.message(player, "core.prefix.game", "game.command.spectatorchat.not-spectating");
            return false;
        }
        return true;
    }
}
