package me.mykindos.betterpvp.core.command.commands.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.chat.channels.ChatChannel;
import me.mykindos.betterpvp.core.chat.channels.IChatChannel;
import me.mykindos.betterpvp.core.chat.channels.StaffChatChannel;
import me.mykindos.betterpvp.core.chat.events.ChatSentEvent;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilFormat;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Sends one message to staff chat, or toggles the executor's chat channel between staff and server.
 */
@Singleton
public class StaffChatCommand extends BrigadierCommand {

    private final Core core;
    private final IChatChannel staffChatChannel;

    @Inject
    public StaffChatCommand(ClientManager clientManager, Core core) {
        super(clientManager);
        this.core = core;
        this.staffChatChannel = new StaffChatChannel(clientManager);
        getAliases().add("sc");
    }

    @Override
    public String getName() {
        return "staffchat";
    }

    @Override
    public String getDescription() {
        return "Toggle staff only chat";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.staff-chat.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Gamer gamer = getClientFromExecutor(context).getGamer();
                    gamer.setChatChannel(gamer.getChatChannel().equals(staffChatChannel) ? ChatChannel.SERVER : ChatChannel.STAFF);
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            UtilServer.callEventAsync(core, new ChatSentEvent(player, staffChatChannel,
                                    Component.text(UtilFormat.spoofNameForLunar(player.getName()) + ": "),
                                    Component.text(context.getArgument("message", String.class))));
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
