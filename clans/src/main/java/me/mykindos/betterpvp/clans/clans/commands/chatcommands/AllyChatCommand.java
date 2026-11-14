package me.mykindos.betterpvp.clans.clans.commands.chatcommands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Optional;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.Clan;
import me.mykindos.betterpvp.clans.clans.ClanManager;
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
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Sends one message to the executor's alliance chat, or toggles their chat channel between alliance and server.
 */
@Singleton
public class AllyChatCommand extends BrigadierCommand {

    private final Clans clans;
    private final ClanManager clanManager;

    @Inject
    public AllyChatCommand(ClientManager clientManager, Clans clans, ClanManager clanManager) {
        super(clientManager);
        this.clans = clans;
        this.clanManager = clanManager;
        getAliases().add("ac");
    }

    @Override
    public String getName() {
        return "allychat";
    }

    @Override
    public String getDescription() {
        return "Toggle ally only chat";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.ally-chat.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    final Gamer gamer = getClientFromExecutor(context).getGamer();
                    clan(player).ifPresent(clan -> gamer.setChatChannel(gamer.getChatChannel().equals(clan.getAllianceChatChannel())
                            ? ChatChannel.SERVER : ChatChannel.ALLIANCE));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final String message = context.getArgument("message", String.class);
                            clan(player).ifPresent(clan -> UtilServer.callEventAsync(clans, new ChatSentEvent(player, clan.getAllianceChatChannel(),
                                    Component.text(UtilFormat.spoofNameForLunar(player.getName()) + ": "), Component.text(message))));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private Optional<Clan> clan(Player player) {
        final Optional<Clan> clan = clanManager.getClanByPlayer(player);
        if (clan.isEmpty()) {
            UtilMessage.message(player, "clans.prefix", "clans.command.ally-chat.no-clan");
        }
        return clan;
    }
}
