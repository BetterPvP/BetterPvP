package me.mykindos.betterpvp.core.command.commands.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.config.Config;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Sends the Discord invite as a clickable link.
 */
@Singleton
public class DiscordCommand extends BrigadierCommand {

    @Inject
    @Config(path = "discord.invite", defaultValue = "https://discord.gg/betterpvp")
    private String discordInvite;

    @Inject
    public DiscordCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "discord";
    }

    @Override
    public String getDescription() {
        return "View the discord invite link";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.discord.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), "core.prefix.command", "core.command.discord.message",
                            Translations.component("core.command.discord.message.click")
                                    .color(NamedTextColor.YELLOW)
                                    .clickEvent(ClickEvent.openUrl(discordInvite)));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
