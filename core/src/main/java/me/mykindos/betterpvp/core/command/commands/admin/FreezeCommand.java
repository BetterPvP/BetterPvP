package me.mykindos.betterpvp.core.command.commands.admin;

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
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Freezes a player in place, or unfreezes them if they are already frozen, and tells staff.
 */
@Singleton
public class FreezeCommand extends BrigadierCommand {

    private final EffectManager effectManager;

    @Inject
    public FreezeCommand(ClientManager clientManager, EffectManager effectManager) {
        super(clientManager);
        this.effectManager = effectManager;
        getAliases().add("unfreeze");
    }

    @Override
    public String getName() {
        return "freeze";
    }

    @Override
    public String getDescription() {
        return "Freeze/unfreeze another player";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.freeze.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .executes(context -> {
                            final CommandSender sender = context.getSource().getSender();
                            final String targetName = context.getArgument("player", String.class);
                            final Player target = Bukkit.getPlayer(targetName);
                            if (target == null) {
                                UtilMessage.message(sender, "core.prefix.freeze", "core.command.freeze.target_not_found", targetName);
                                return Command.SINGLE_SUCCESS;
                            }
                            final boolean unfreeze = effectManager.hasEffect(target, EffectTypes.FROZEN);
                            if (unfreeze) {
                                UtilMessage.message(target, "core.prefix.freeze", "core.command.freeze.unfrozen_target");
                                effectManager.removeEffect(target, EffectTypes.FROZEN);
                            } else {
                                UtilMessage.message(target, "core.prefix.freeze", "core.command.freeze.frozen_target");
                                effectManager.addEffect(target, null, EffectTypes.FROZEN, "Freeze", 1, 1000, true, true);
                            }
                            clientManager.sendMessageToRank("core.prefix.freeze",
                                    Translations.component(unfreeze ? "core.command.freeze.unfrozen_staff" : "core.command.freeze.frozen_staff",
                                            Component.text(sender.getName(), NamedTextColor.YELLOW),
                                            Component.text(target.getName(), NamedTextColor.YELLOW)),
                                    Rank.TRIAL_MOD);
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
