package me.mykindos.betterpvp.clans.world;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mykindos.betterpvp.core.client.repository.ClientManager;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierCommand;
import me.mykindos.betterpvp.core.command.brigadier.BrigadierNodes;
import me.mykindos.betterpvp.core.locale.Translations;
import me.mykindos.betterpvp.core.utilities.UtilMessage;
import me.mykindos.betterpvp.core.world.model.BPvPWorld;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Queues chunks in the main world for tree-respawn evaluation: {@code /treerespawn} queues the chunk the executor
 * stands in, {@code /treerespawn <chunkX> <chunkZ>} a loaded chunk by its coordinates, and {@code /treerespawn status}
 * shows the queue.
 */
@Singleton
public class TreeRespawnCommand extends BrigadierCommand {

    private final TreeRespawnManager treeRespawnManager;

    @Inject
    public TreeRespawnCommand(ClientManager clientManager, TreeRespawnManager treeRespawnManager) {
        super(clientManager);
        this.treeRespawnManager = treeRespawnManager;
    }

    @Override
    public String getName() {
        return "treerespawn";
    }

    @Override
    public String getDescription() {
        return "Queues a chunk for tree-respawn evaluation";
    }

    @Override
    public Component getDescriptionComponent() {
        return Translations.component("clans.command.tree-respawn.description");
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> define() {
        return BrigadierNodes.literal(getName())
                .executes(context -> {
                    final Player player = getPlayerFromExecutor(context);
                    if (mainWorld(player) == null) {
                        return Command.SINGLE_SUCCESS;
                    }
                    if (!player.getWorld().getName().equalsIgnoreCase(BPvPWorld.MAIN_WORLD_NAME)) {
                        UtilMessage.message(player, "clans.prefix", "clans.command.tree-respawn.not-main-world");
                        return Command.SINGLE_SUCCESS;
                    }
                    queueAndReport(player, player.getChunk());
                    return Command.SINGLE_SUCCESS;
                })
                .then(BrigadierNodes.literal("status")
                        .executes(context -> {
                            final CommandSender sender = context.getSource().getSender();
                            if (mainWorld(sender) != null) {
                                UtilMessage.message(sender, "clans.prefix", "clans.command.tree-respawn.status",
                                        Component.text(treeRespawnManager.getQueueSize(), NamedTextColor.YELLOW),
                                        Component.text(treeRespawnManager.isEnabled(), NamedTextColor.YELLOW));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(BrigadierNodes.argument("chunkX", IntegerArgumentType.integer())
                        .then(BrigadierNodes.argument("chunkZ", IntegerArgumentType.integer())
                                .executes(context -> {
                                    final CommandSender sender = context.getSource().getSender();
                                    final World world = mainWorld(sender);
                                    if (world == null) {
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    final int chunkX = context.getArgument("chunkX", Integer.class);
                                    final int chunkZ = context.getArgument("chunkZ", Integer.class);
                                    if (!world.isChunkLoaded(chunkX, chunkZ)) {
                                        UtilMessage.message(sender, "clans.prefix", "clans.command.tree-respawn.chunk-not-loaded",
                                                Component.text(chunkX, NamedTextColor.YELLOW), Component.text(chunkZ, NamedTextColor.YELLOW));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    queueAndReport(sender, world.getChunkAt(chunkX, chunkZ));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    /**
     * @return the main world, or {@code null} after telling the sender it is not loaded
     */
    private static World mainWorld(CommandSender sender) {
        final World world = Bukkit.getWorld(BPvPWorld.MAIN_WORLD_NAME);
        if (world == null) {
            UtilMessage.message(sender, "clans.prefix", "clans.command.tree-respawn.no-world");
        }
        return world;
    }

    private void queueAndReport(CommandSender sender, Chunk chunk) {
        final int sizeBefore = treeRespawnManager.getQueueSize();
        treeRespawnManager.queueChunk(chunk);
        final int sizeAfter = treeRespawnManager.getQueueSize();
        if (sizeAfter > sizeBefore) {
            UtilMessage.message(sender, "clans.prefix", "clans.command.tree-respawn.queued",
                    Component.text(chunk.getX(), NamedTextColor.YELLOW), Component.text(chunk.getZ(), NamedTextColor.YELLOW),
                    Component.text(sizeAfter, NamedTextColor.YELLOW));
        } else {
            UtilMessage.message(sender, "clans.prefix", "clans.command.tree-respawn.not-queued",
                    Component.text(chunk.getX(), NamedTextColor.YELLOW), Component.text(chunk.getZ(), NamedTextColor.YELLOW));
        }
    }
}
