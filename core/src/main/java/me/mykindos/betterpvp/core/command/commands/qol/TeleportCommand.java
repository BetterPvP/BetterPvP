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
 * Teleports the executor to a player, or one player to another.
 */
@Singleton
public class TeleportCommand extends BrigadierCommand {

    @Inject
    public TeleportCommand(ClientManager clientManager) {
        super(clientManager);
        getAliases().add("tp");
    }

    @Override
    public String getName() {
        return "teleport";
    }

    @Override
    public String getDescription() {
        return "Teleport to a target player";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.teleport.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final Player target = Bukkit.getPlayer(context.getArgument("player", String.class));
                            if (target != null) {
                                player.teleport(target.getLocation());
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(BrigadierNodes.argument("destination", BPvPArgumentTypes.playerName())
                                .executes(context -> {
                                    final Player from = Bukkit.getPlayer(context.getArgument("player", String.class));
                                    final Player to = Bukkit.getPlayer(context.getArgument("destination", String.class));
                                    if (from != null && to != null) {
                                        from.teleport(to.getLocation());
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })));
    }
}
