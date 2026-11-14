package me.mykindos.betterpvp.core.command.commands.general;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import me.mykindos.betterpvp.core.world.WorldHandler;
import me.mykindos.betterpvp.core.world.events.SpawnTeleportEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Teleports the executor to spawn, through the spawn-teleport event so other features can delay or cancel it.
 */
@Singleton
public class SpawnCommand extends BrigadierCommand {

    private final WorldHandler worldHandler;

    @Inject
    public SpawnCommand(ClientManager clientManager, WorldHandler worldHandler) {
        super(clientManager);
        this.worldHandler = worldHandler;
    }

    @Override
    public String getName() {
        return "spawn";
    }

    @Override
    public String getDescription() {
        return "Teleport to spawn";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("core.command.spawn.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    UtilServer.callEvent(new SpawnTeleportEvent(player, () -> player.teleport(worldHandler.getSpawnLocation())));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
