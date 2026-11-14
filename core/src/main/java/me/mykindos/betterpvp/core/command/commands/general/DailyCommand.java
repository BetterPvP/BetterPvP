package me.mykindos.betterpvp.core.command.commands.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.gamer.Gamer;
import me.mykindos.betterpvp.core.client.gamer.properties.GamerProperty;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.config.Config;
import me.mykindos.betterpvp.core.cooldowns.CooldownManager;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilFormat;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Pays the executor a coin reward once a day.
 */
@Singleton
public class DailyCommand extends BrigadierCommand {

    @Inject
    @Config(path = "daily.coinsAmount", defaultValue = "5000")
    private int coinsAmount;

    private final CooldownManager cooldownManager;

    @Inject
    public DailyCommand(ClientManager clientManager, CooldownManager cooldownManager) {
        super(clientManager);
        this.cooldownManager = cooldownManager;
    }

    @Override
    public String getName() {
        return "daily";
    }

    @Override
    public String getDescription() {
        return "Claim your daily rewards";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.daily.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    final Client client = getClientFromExecutor(context);
                    if (!cooldownManager.use(player, "Daily", 86400000, false, false)) {
                        UtilMessage.message(player, "core.prefix.command", "core.command.daily.already");
                        return Command.SINGLE_SUCCESS;
                    }
                    final Gamer gamer = client.getGamer();
                    gamer.saveProperty(GamerProperty.BALANCE, (int) gamer.getProperty(GamerProperty.BALANCE).orElse(0) + coinsAmount);
                    UtilMessage.message(player, "core.prefix.command", "core.command.daily.claimed",
                            Component.text(UtilFormat.formatNumber(coinsAmount)).color(NamedTextColor.GREEN));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
