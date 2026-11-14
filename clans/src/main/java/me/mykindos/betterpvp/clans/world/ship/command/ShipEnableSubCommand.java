package me.mykindos.betterpvp.clans.world.ship.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
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
 * {@code /ship enable <berth|*>} moors a disabled vessel again, cancelling any timer that would have done it.
 */
@Singleton
@BrigadierSubCommand(ShipCommand.class)
public class ShipEnableSubCommand extends BrigadierCommand {

    private final ShipService ships;

    @Inject
    public ShipEnableSubCommand(ClientManager clientManager, @NotNull ShipService ships) {
        super(clientManager);
        this.ships = ships;
    }

    @Override
    public String getName() {
        return "enable";
    }

    @Override
    public String getDescription() {
        return "Moor a disabled ship again";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.ship.enable.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.literal(BerthArgument.ALL)
                        .executes(context -> enable(context, BerthArgument.ALL)))
                .then(BerthArgument.berth(ships)
                        .executes(context -> enable(context, context.getArgument("berth", String.class))));
    }

    private int enable(CommandContext<CommandSourceStack> context, String berth) throws CommandSyntaxException {
        final Player player = getPlayerFromExecutor(context);
        final List<String> berthIds = BerthArgument.resolve(player, ships, berth);
        if (berthIds.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        ships.enable(player.getWorld(), berthIds);
        UtilMessage.message(player, "clans.prefix.ships", Translations.component("clans.command.ship.enable.success",
                Component.text(berthIds.size(), NamedTextColor.YELLOW)).color(NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }
}
