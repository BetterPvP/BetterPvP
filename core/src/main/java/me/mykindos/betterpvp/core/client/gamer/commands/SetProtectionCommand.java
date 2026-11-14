package me.mykindos.betterpvp.core.client.gamer.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.client.gamer.properties.GamerProperty;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.arguments.BPvPArgumentTypes;
import me.mykindos.betterpvp.core.effects.EffectManager;
import me.mykindos.betterpvp.core.effects.EffectTypes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilTime;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Singleton
public class SetProtectionCommand extends BrigadierCommand {
    private final EffectManager effectManager;

    @Inject
    public SetProtectionCommand(ClientManager clientManager, EffectManager effectManager) {
        super(clientManager);
        this.effectManager = effectManager;
    }

    @Override
    public String getName() {
        return "setprotection";
    }

    @Override
    public String getDescription() {
        return "Set a players PvP protection in minutes";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.set-protection.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    execute(getPlayerFromExecutor(context), getClientFromExecutor(context));
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("player", BPvPArgumentTypes.playerName())
                        .then(BrigadierNodes.argument("minutes", DoubleArgumentType.doubleArg(0))
                                .executes(context -> {
                                    execute(getPlayerFromExecutor(context), getClientFromExecutor(context),
                                            context.getArgument("player", String.class),
                                            String.valueOf(context.getArgument("minutes", Double.class)));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    public String getUsage() {
        return getName() + " <player> <duration>";
    }

    private void execute(Player player, Client client, String... args) {
        if (args.length < 2) {
            UtilMessage.message(player, "core.prefix.protection", "core.command.setprotection.usage",
                    Component.text(getUsage()));
            return;
        }

        clientManager.search().offline(args[0]).thenAccept(clientOptional -> {
            if (clientOptional.isEmpty()) {
                UtilMessage.message(player, "core.prefix.protection", "core.command.protection.invalid_player",
                        Component.text(args[0], NamedTextColor.YELLOW));
                return;
            }

            double duration;
            try {
                duration = Double.parseDouble(args[1]);
                if (duration < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException ignored) {
                UtilMessage.message(player, "core.prefix.protection", "core.command.setprotection.invalid_duration",
                        Component.text(args[1], NamedTextColor.GREEN));
                return;
            }

            long newDuration = (long) (duration * 60 * 1000L);
            String timeString = UtilTime.getTime(newDuration, 1);
            Client target = clientOptional.get();

            target.getGamer().saveProperty(GamerProperty.REMAINING_PVP_PROTECTION, newDuration);
            Player targetPlayer = Bukkit.getPlayer(target.getUniqueId());
            if (targetPlayer != null) {
                effectManager.removeEffect(targetPlayer, EffectTypes.PROTECTION);
                effectManager.addEffect(targetPlayer, EffectTypes.PROTECTION, newDuration);
                UtilMessage.message(targetPlayer, "core.prefix.protection", "core.command.setprotection.target_set",
                        Component.text(timeString, NamedTextColor.GREEN));
            }

            clientManager.sendMessageToRank("core.prefix.protection",
                    Translations.component("core.command.setprotection.broadcast",
                            Component.text(player.getName(), NamedTextColor.YELLOW),
                            Component.text(target.getName(), NamedTextColor.YELLOW),
                            Component.text(timeString, NamedTextColor.GREEN)
                    ),
                    Rank.TRIAL_MOD);
        });

    }

}
