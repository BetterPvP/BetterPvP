package me.mykindos.betterpvp.core.settings.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.settings.menus.SettingsMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Opens the executor's settings menu.
 */
@Singleton
public class SettingsCommand extends BrigadierCommand {

    @Inject
    public SettingsCommand(ClientManager clientManager) {
        super(clientManager);
        getAliases().add("prefs");
    }

    @Override
    public String getName() {
        return "settings";
    }

    @Override
    public String getDescription() {
        return "View or modify your settings";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.settings.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    new SettingsMenu(player, getClientFromExecutor(context)).show(player);
                    return Command.SINGLE_SUCCESS;
                });
    }
}
