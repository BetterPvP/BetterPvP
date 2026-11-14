package me.mykindos.betterpvp.progression.booster.commands;

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
import me.mykindos.betterpvp.core.utilities.UtilTime;
import me.mykindos.betterpvp.progression.booster.BoosterManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Shows how long the executor's profession experience booster has left.
 */
@Singleton
public class BoosterCommand extends BrigadierCommand {

    private final BoosterManager boosterManager;

    @Inject
    public BoosterCommand(ClientManager clientManager, BoosterManager boosterManager) {
        super(clientManager);
        this.boosterManager = boosterManager;
    }

    @Override
    public String getName() {
        return "booster";
    }

    @Override
    public String getDescription() {
        return "Check your remaining profession experience booster time";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("progression.command.booster.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    final long remaining = boosterManager.getRemainingTime(player.getUniqueId());
                    if (remaining <= 0) {
                        UtilMessage.message(player, "core.prefix.booster", "progression.booster.none-active");
                    } else {
                        UtilMessage.message(player, "core.prefix.booster", "progression.booster.remaining",
                                Component.text(UtilTime.getTime(remaining, 1), NamedTextColor.GREEN));
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }
}
