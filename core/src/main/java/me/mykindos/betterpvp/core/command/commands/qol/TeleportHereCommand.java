package me.mykindos.betterpvp.core.command.commands.qol;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Teleports a player to the executor.
 */
@Singleton
public class TeleportHereCommand extends BrigadierCommand {

    @Inject
    public TeleportHereCommand(ClientManager clientManager) {
        super(clientManager);
        getAliases().add("tphere");
    }

    @Override
    public String getName() {
        return "teleporthere";
    }

    @Override
    public String getDescription() {
        return "Teleport a target player to yourself";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.teleport-here.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final Player target = Bukkit.getPlayer(context.getArgument("player", String.class));
                            if (target != null) {
                                target.teleport(player.getLocation());
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
