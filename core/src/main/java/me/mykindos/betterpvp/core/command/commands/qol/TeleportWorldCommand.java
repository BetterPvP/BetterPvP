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
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.world.generator.VoidWorldGenerator;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;

/**
 * Teleports the executor to a world's spawn, creating the world first if it does not exist. A new world can be
 * made void or flat.
 */
@Singleton
public class TeleportWorldCommand extends BrigadierCommand {

    @Inject
    public TeleportWorldCommand(ClientManager clientManager) {
        super(clientManager);
        getAliases().add("tpworld");
        getAliases().add("tpw");
    }

    @Override
    public String getName() {
        return "teleportworld";
    }

    @Override
    public String getDescription() {
        return "Teleport to a specific world.";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.teleport-world.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .then(BrigadierNodes.argument("world", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            Bukkit.getWorlds().stream().map(World::getName)
                                    .filter(name -> name.toLowerCase().startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            teleport(getPlayerFromExecutor(context), context.getArgument("world", String.class), "");
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(BrigadierNodes.argument("type", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    builder.suggest("void");
                                    builder.suggest("flat");
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    teleport(getPlayerFromExecutor(context), context.getArgument("world", String.class),
                                            context.getArgument("type", String.class));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private static void teleport(Player player, String worldName, String type) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            UtilMessage.message(player, "core.prefix.teleport", "core.command.teleportworld.creating");
            final WorldCreator creator = new WorldCreator(worldName).generateStructures(false);
            if (type.equalsIgnoreCase("void")) {
                creator.generator(new VoidWorldGenerator());
            } else if (type.equalsIgnoreCase("flat")) {
                creator.type(WorldType.FLAT);
            }
            world = creator.createWorld();
        }
        if (world == null) {
            UtilMessage.message(player, "core.prefix.teleport", "core.command.teleportworld.failed");
            return;
        }
        player.teleport(world.getSpawnLocation());
    }
}
