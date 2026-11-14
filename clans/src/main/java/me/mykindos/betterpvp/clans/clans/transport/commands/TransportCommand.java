package me.mykindos.betterpvp.clans.clans.transport.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.clans.clans.ClanManager;
import me.mykindos.betterpvp.clans.clans.transport.ClanTravelHubMenu;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Opens the travel hub menu.
 */
@Singleton
public class TransportCommand extends BrigadierCommand {

    private final ClanManager clanManager;

    @Inject
    public TransportCommand(ClientManager clientManager, ClanManager clanManager) {
        super(clientManager);
        this.clanManager = clanManager;
    }

    @Override
    public String getName() {
        return "transport";
    }

    @Override
    public String getDescription() {
        return "Opens the travel hub UI";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.transport.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    new ClanTravelHubMenu(player, getClientFromExecutor(context), clanManager).show(player);
                    return Command.SINGLE_SUCCESS;
                });
    }
}
