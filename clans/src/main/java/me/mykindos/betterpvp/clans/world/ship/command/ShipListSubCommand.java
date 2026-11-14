package me.mykindos.betterpvp.clans.world.ship.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import me.mykindos.betterpvp.clans.world.ship.Berth;
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
 * {@code /ship list} shows every mooring in the executor's world, and what is tied up at it.
 */
@Singleton
@BrigadierSubCommand(ShipCommand.class)
public class ShipListSubCommand extends BrigadierCommand {

    private final ShipService ships;

    @Inject
    public ShipListSubCommand(ClientManager clientManager, @NotNull ShipService ships) {
        super(clientManager);
        this.ships = ships;
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getDescription() {
        return "List the moorings in this world";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.ship.list.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    final List<Berth> berths = ships.berths(player.getWorld());
                    if (berths.isEmpty()) {
                        UtilMessage.message(player, "clans.prefix.ships", "clans.command.ship.no-moorings");
                        return Command.SINGLE_SUCCESS;
                    }
                    UtilMessage.message(player, "clans.prefix.ships", "clans.command.ship.list.header",
                            Component.text(berths.size(), NamedTextColor.YELLOW), Component.text(player.getWorld().getName(), NamedTextColor.AQUA));
                    for (Berth berth : berths) {
                        UtilMessage.message(player, "clans.prefix.ships", Component.text(berth.getId(), NamedTextColor.WHITE)
                                .appendSpace()
                                .append(state(player, berth)));
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }

    /**
     * What is at this berth. A disabled berth and a genuinely empty one both hold no vessel, so the service is asked
     * which it is rather than guessing from the missing structure name.
     */
    private @NotNull Component state(@NotNull Player player, @NotNull Berth berth) {
        if (ships.isDisabled(player.getWorld(), berth.getId())) {
            return Translations.component("clans.command.ship.list.disabled").color(NamedTextColor.RED);
        }
        if (!berth.isCrewable()) {
            return Translations.component("clans.command.ship.list.empty").color(NamedTextColor.GRAY);
        }
        return Component.text(berth.getStructure(), NamedTextColor.GREEN)
                .appendSpace()
                .append(Translations.component("clans.command.ship.list.capacity", Component.text(berth.getCapacity())).color(NamedTextColor.GRAY));
    }
}
