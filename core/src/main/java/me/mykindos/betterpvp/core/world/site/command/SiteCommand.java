package me.mykindos.betterpvp.core.world.site.command;

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
import net.kyori.adventure.text.Component;

/**
 * Admin tooling for sites and their live instances: {@code list}, {@code create}, {@code tp} and {@code delete}.
 */
@Singleton
public class SiteCommand extends BrigadierCommand {

    @Inject
    public SiteCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "site";
    }

    @Override
    public String getDescription() {
        return "Site and instance management";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.site.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    UtilMessage.message(context.getSource().getSender(), "core.prefix.sites", "core.command.site.usage");
                    return Command.SINGLE_SUCCESS;
                });
    }
}
