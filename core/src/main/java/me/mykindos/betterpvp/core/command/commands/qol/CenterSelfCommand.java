package me.mykindos.betterpvp.core.command.commands.qol;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Moves the executor to the centre of their block, looking down, or level toward a compass direction.
 */
@Singleton
public class CenterSelfCommand extends BrigadierCommand {

    @Inject
    public CenterSelfCommand(ClientManager clientManager) {
        super(clientManager);
    }

    @Override
    public String getName() {
        return "centerself";
    }

    @Override
    public String getDescription() {
        return "Center yourself on the specified block";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.center-self.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    center(getPlayerFromExecutor(context), "");
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("direction", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            List.of("NORTH", "SOUTH", "EAST", "WEST").stream()
                                    .filter(direction -> direction.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            center(getPlayerFromExecutor(context), context.getArgument("direction", String.class));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static void center(Player player, String direction) {
        final Location location = player.getLocation().clone().toCenterLocation();
        location.setY(location.getBlockY());
        switch (direction.toUpperCase()) {
            case "NORTH" -> location.setRotation(-180, 0);
            case "SOUTH" -> location.setRotation(0, 0);
            case "EAST" -> location.setRotation(-90, 0);
            case "WEST" -> location.setRotation(90, 0);
            default -> location.setRotation(0, 90);
        }
        player.teleport(location);
    }
}
