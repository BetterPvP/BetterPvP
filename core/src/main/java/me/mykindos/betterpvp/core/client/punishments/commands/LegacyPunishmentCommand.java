package me.mykindos.betterpvp.core.client.punishments.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import net.kyori.adventure.text.Component;

@Singleton
public class LegacyPunishmentCommand extends BrigadierCommand {

    @Inject
    public LegacyPunishmentCommand(ClientManager clientManager) {
        super(clientManager);
        getAliases().add("lp");
    }

    @Override
    public String getName() {
        return "legacypunish";
    }

    @Override
    public String getDescription() {
        return "Base command for the legacy punishing system";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.legacy-punishment.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName());
    }
}
