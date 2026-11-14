package me.mykindos.betterpvp.core.command.commands.qol;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilLocation;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Teleports the executor to coordinates, where {@code ~} is relative to where they stand.
 */
@Singleton
public class TeleportPositionCommand extends BrigadierCommand {

    @Inject
    public TeleportPositionCommand(ClientManager clientManager) {
        super(clientManager);
        getAliases().add("tppos");
    }

    @Override
    public String getName() {
        return "teleportposition";
    }

    @Override
    public String getDescription() {
        return "Teleport to a specific position";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.teleport-position.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("x", StringArgumentType.word())
                        .then(BrigadierNodes.argument("y", StringArgumentType.word())
                                .then(BrigadierNodes.argument("z", StringArgumentType.word())
                                        .executes(context -> {
                                            final Player player = getPlayerFromExecutor(context);
                                            final Location location = UtilLocation.getTeleportLocation(player.getLocation(), new String[]{
                                                    context.getArgument("x", String.class),
                                                    context.getArgument("y", String.class),
                                                    context.getArgument("z", String.class)});
                                            player.teleport(location);
                                            UtilMessage.message(player, "core.prefix.teleport", "core.command.teleportposition.success",
                                                    Component.text(location.getX(), NamedTextColor.GREEN),
                                                    Component.text(location.getY(), NamedTextColor.GREEN),
                                                    Component.text(location.getZ(), NamedTextColor.GREEN));
                                            return Command.SINGLE_SUCCESS;
                                        }))));
    }
}
