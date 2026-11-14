package me.mykindos.betterpvp.core.command.commands.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.effects.EffectManager;
import me.mykindos.betterpvp.core.effects.EffectTypes;
import me.mykindos.betterpvp.core.framework.events.kill.PlayerSuicideEvent;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Kills the executor, unless they are frozen. An admin can name another player to kill them instead.
 */
@Singleton
public class KillCommand extends BrigadierCommand {

    private final EffectManager effectManager;

    @Inject
    public KillCommand(ClientManager clientManager, EffectManager effectManager) {
        super(clientManager);
        this.effectManager = effectManager;
    }

    @Override
    public String getName() {
        return "kill";
    }

    @Override
    public String getDescription() {
        return "Kill a player";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.kill.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    if (effectManager.hasEffect(player, EffectTypes.FROZEN)) {
                        UtilMessage.message(player, "core.prefix.command", "core.command.kill.self.blocked");
                        return Command.SINGLE_SUCCESS;
                    }
                    UtilServer.callEvent(new PlayerSuicideEvent(player, () -> {
                        player.setHealth(0);
                        UtilMessage.message(player, "core.prefix.command", "core.command.kill.self.success");
                    }));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName(), source -> senderHasRank(source, Rank.ADMIN))
                        .executes(context -> {
                            final Player target = Bukkit.getPlayer(context.getArgument("player", String.class));
                            if (target != null) {
                                target.setHealth(0);
                                UtilMessage.message(context.getSource().getSender(), "core.prefix.command", "core.command.kill.other.success",
                                        Component.text(target.getName(), NamedTextColor.YELLOW));
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
