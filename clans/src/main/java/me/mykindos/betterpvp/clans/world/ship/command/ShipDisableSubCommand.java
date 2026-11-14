package me.mykindos.betterpvp.clans.world.ship.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import me.mykindos.betterpvp.clans.world.ship.ShipService;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierSubCommand;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /ship disable <berth|*> [minutes]} un-moors a vessel and hands its blocks back, so the dock underneath shows
 * as the builder made it. Without minutes it stays off until {@code /ship enable}.
 */
@Singleton
@BrigadierSubCommand(ShipCommand.class)
public class ShipDisableSubCommand extends BrigadierCommand {

    private final ShipService ships;

    @Inject
    public ShipDisableSubCommand(ClientManager clientManager, @NotNull ShipService ships) {
        super(clientManager);
        this.ships = ships;
    }

    @Override
    public String getName() {
        return "disable";
    }

    @Override
    public String getDescription() {
        return "Un-moor a ship and restore the blocks it was pasted over";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.ship.disable.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.literal(BerthArgument.ALL)
                        .executes(context -> disable(context, BerthArgument.ALL, 0))
                        .then(BrigadierNodes.argument("minutes", LongArgumentType.longArg(1))
                                .executes(context -> disable(context, BerthArgument.ALL, context.getArgument("minutes", Long.class)))))
                .then(BerthArgument.berth(ships)
                        .executes(context -> disable(context, context.getArgument("berth", String.class), 0))
                        .then(BrigadierNodes.argument("minutes", LongArgumentType.longArg(1))
                                .executes(context -> disable(context, context.getArgument("berth", String.class),
                                        context.getArgument("minutes", Long.class)))));
    }

    /**
     * @param minutes how long the ship stays off, or 0 to leave it off until re-enabled
     */
    private int disable(CommandContext<CommandSourceStack> context, String berth, long minutes) throws CommandSyntaxException {
        final Player player = getPlayerFromExecutor(context);
        final List<String> berthIds = BerthArgument.resolve(player, ships, berth);
        if (berthIds.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        ships.disable(player.getWorld(), berthIds, minutes * 60_000L);
        final Component count = Component.text(berthIds.size(), NamedTextColor.YELLOW);
        UtilMessage.message(player, "clans.prefix.ships", minutes > 0
                ? Translations.component("clans.command.ship.disable.success-timed", count, Component.text(minutes)).color(NamedTextColor.GREEN)
                : Translations.component("clans.command.ship.disable.success", count).color(NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }
}
