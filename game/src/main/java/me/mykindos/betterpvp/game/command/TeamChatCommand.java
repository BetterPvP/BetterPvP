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
import me.mykindos.betterpvp.game.framework.ServerController;
import me.mykindos.betterpvp.game.framework.TeamGame;
import me.mykindos.betterpvp.game.framework.model.team.Team;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * Sends one message to the executor's team chat, or toggles their chat channel between team and server. Only works
 * in a team game, for a player on a team.
 */
@Singleton
public class TeamChatCommand extends BrigadierCommand {

    private final ServerController serverController;

    @Inject
    public TeamChatCommand(ClientManager clientManager, ServerController serverController) {
        super(clientManager);
        this.serverController = serverController;
        getAliases().add("tc");
    }

    @Override
    public String getName() {
        return "teamchat";
    }

    @Override
    public String getDescription() {
        return "Toggle team only chat";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("game.command.team-chat.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    final Team team = team(player);
                    if (team != null) {
                        final Gamer gamer = getClientFromExecutor(context).getGamer();
                        gamer.setChatChannel(gamer.getChatChannel().equals(team.getTeamChatChannel()) ? ChatChannel.SERVER : ChatChannel.TEAM);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final Team team = team(player);
                            if (team != null) {
                                UtilServer.callEventAsync(JavaPlugin.getPlugin(GamePlugin.class), new ChatSentEvent(player, team.getTeamChatChannel(),
                                        Component.text(UtilFormat.spoofNameForLunar(player.getName()) + ": "),
                                        Component.text(context.getArgument("message", String.class))));
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    /**
     * @return the executor's team, or null after telling them why they have none
     */
    private @Nullable Team team(Player player) {
        if (!(serverController.getCurrentGame() instanceof TeamGame<?> teamGame)) {
            UtilMessage.message(player, "core.prefix.game", "game.command.teamchat.not-team-game");
            return null;
        }
        final Team team = teamGame.getPlayerTeam(player);
        if (team == null) {
            UtilMessage.message(player, "core.prefix.clans", "game.command.teamchat.not-on-team");
        }
        return team;
    }
}
