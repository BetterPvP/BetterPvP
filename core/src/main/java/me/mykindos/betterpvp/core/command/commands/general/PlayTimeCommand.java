package me.mykindos.betterpvp.core.command.commands.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Duration;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.gamer.properties.GamerProperty;
import me.mykindos.betterpvp.core.client.properties.ClientProperty;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilTime;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Shows the executor's total time played, and this season's when Clans is running.
 */
@Singleton
public class PlayTimeCommand extends BrigadierCommand {

    @Inject
    public PlayTimeCommand(ClientManager clientManager) {
        super(clientManager);
        getAliases().add("timeplayed");
    }

    @Override
    public String getName() {
        return "playtime";
    }

    @Override
    public String getDescription() {
        return "Check your time played";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.play-time.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    final Client client = getClientFromExecutor(context);
                    final String total = UtilTime.humanReadableFormat(Duration.ofMillis((Long) client.getProperty(ClientProperty.TIME_PLAYED).orElse(0L)));
                    UtilMessage.message(player, "core.prefix.command", "core.command.playtime.total", total);
                    if (Bukkit.getPluginManager().getPlugin("Clans") != null) {
                        final String season = UtilTime.humanReadableFormat(Duration.ofMillis((Long) client.getGamer().getProperty(GamerProperty.TIME_PLAYED).orElse(0L)));
                        UtilMessage.message(player, "core.prefix.command", "core.command.playtime.season", season);
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }
}
