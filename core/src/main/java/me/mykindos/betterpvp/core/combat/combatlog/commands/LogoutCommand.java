package me.mykindos.betterpvp.core.combat.combatlog.commands;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.combat.combatlog.events.PlayerCombatLogEvent;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Tells the executor whether logging out now would leave a combat log behind.
 */
@Singleton
public class LogoutCommand extends BrigadierCommand {

    @Inject
    public LogoutCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "logout";
    }

    @Override
    public String getDescription() {
        return "Informs the player on whether they are safe to logout";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.logout.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    final PlayerCombatLogEvent event = UtilServer.callEvent(new PlayerCombatLogEvent(getClientFromExecutor(context), player));
                    UtilMessage.message(player, "core.prefix.logout", "core.command.logout.status",
                            Translations.component(event.isSafe() ? "core.command.logout.safe" : "core.command.logout.unsafe")
                                    .color(event.isSafe() ? NamedTextColor.GREEN : NamedTextColor.RED));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
