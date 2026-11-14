package me.mykindos.betterpvp.core.chat.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.chat.events.ChatSentEvent;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Toggles a server-wide chat silence. While it is on, only trial moderators and above can talk.
 */
@Singleton
@BPvPListener
public class SilenceChatCommand extends BrigadierCommand implements Listener {

    private boolean isChatSilenced = false;

    @Inject
    public SilenceChatCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "silencechat";
    }

    @Override
    public String getDescription() {
        return "Prevent non-ranked players from sending messages";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.silence-chat.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    isChatSilenced = !isChatSilenced;
                    UtilMessage.broadcast("core.prefix.chat", isChatSilenced ? "core.command.silencechat.on" : "core.command.silencechat.off");
                    return Command.SINGLE_SUCCESS;
                });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChatSent(ChatSentEvent event) {
        if (isChatSilenced && !clientManager.search().online(event.getPlayer()).hasRank(Rank.TRIAL_MOD)) {
            UtilMessage.message(event.getPlayer(), "core.prefix.chat", "core.command.silencechat.denied");
            event.cancel("Chat is silenced");
        }
    }
}
