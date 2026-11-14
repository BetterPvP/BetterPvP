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
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilPlayer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Heals the executor, or a named online player, to full health.
 */
@Singleton
public class HealCommand extends BrigadierCommand {

    @Inject
    public HealCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "heal";
    }

    @Override
    public String getDescription() {
        return "Heal yourself or another player to full health";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.heal.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    player.setHealth(UtilPlayer.getMaxHealth(player));
                    UtilMessage.message(player, "core.prefix.heal", "core.command.heal.self.success");
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .executes(context -> {
                            final Player target = Bukkit.getPlayer(context.getArgument("player", String.class));
                            if (target != null) {
                                target.setHealth(UtilPlayer.getMaxHealth(target));
                                UtilMessage.message(context.getSource().getSender(), "core.prefix.heal", "core.command.heal.other.success",
                                        Component.text(target.getName()));
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
