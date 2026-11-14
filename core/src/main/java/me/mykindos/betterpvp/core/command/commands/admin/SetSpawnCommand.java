package me.mykindos.betterpvp.core.command.commands.admin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.utilities.UtilWorld;
import me.mykindos.betterpvp.core.world.WorldHandler;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Sets the world's spawn where the executor stands, or saves a named spawn there.
 */
@Singleton
public class SetSpawnCommand extends BrigadierCommand {

    private final Core core;
    private final WorldHandler worldHandler;

    @Inject
    public SetSpawnCommand(ClientManager clientManager, Core core, WorldHandler worldHandler) {
        super(clientManager);
        this.core = core;
        this.worldHandler = worldHandler;
    }

    @Override
    public String getName() {
        return "setspawn";
    }

    @Override
    public String getDescription() {
        return "Set the spawn point for the current world";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.set-spawn.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    player.getWorld().setSpawnLocation(player.getLocation());
                    UtilMessage.message(player, "core.prefix.spawn", "core.command.setspawn.main.success");
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.argument("name", StringArgumentType.greedyString())
                        .executes(context -> {
                            final Player player = getPlayerFromExecutor(context);
                            final String spawnName = context.getArgument("name", String.class);
                            worldHandler.getSpawnLocations().put(spawnName, player.getLocation());
                            core.getConfig().set("spawns." + spawnName, UtilWorld.locationToString(player.getLocation(), false));
                            core.saveConfig();
                            UtilMessage.message(player, "core.prefix.spawn", Translations.component("core.command.setspawn.named.success",
                                    Component.text(spawnName),
                                    Component.text(UtilWorld.locationToString(player.getLocation()))));
                            return Command.SINGLE_SUCCESS;
                        }));
    }
}
